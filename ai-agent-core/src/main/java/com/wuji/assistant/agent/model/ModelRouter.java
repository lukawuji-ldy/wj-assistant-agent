package com.wuji.assistant.agent.model;

import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.observability.AgentTelemetry;
import com.wuji.assistant.agent.observability.LlmUsageHolder;
import com.wuji.assistant.agent.observability.TokenUsageExtractor;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * 模型主备路由：同配置退避重试 + fallbacks 故障转移。
 *
 * @author liudy
 */
@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final LlmClientFactory llmClientFactory;
    private final WujiModelProperties modelProperties;
    private final LlmCallAuditor llmCallAuditor;
    private final AgentTelemetry agentTelemetry;

    public ModelRouter(LlmClientFactory llmClientFactory,
                       WujiModelProperties modelProperties,
                       LlmCallAuditor llmCallAuditor,
                       AgentTelemetry agentTelemetry) {
        this.llmClientFactory = llmClientFactory;
        this.modelProperties = modelProperties;
        this.llmCallAuditor = llmCallAuditor;
        this.agentTelemetry = agentTelemetry;
    }

    /**
     * 主备有序 configId 列表（去重、去空）。
     *
     * @return id 列表
     */
    public List<String> orderedConfigIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (StringUtils.hasText(modelProperties.getPrimaryConfigId())) {
            ids.add(modelProperties.getPrimaryConfigId().trim());
        }
        if (modelProperties.getFallbackConfigIds() != null) {
            for (String id : modelProperties.getFallbackConfigIds()) {
                if (StringUtils.hasText(id)) {
                    ids.add(id.trim());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 尝试打开指定配置的客户端；不可用返回 empty（并打日志）。
     *
     * @param configId 配置键
     * @return 路由客户端
     */
    public Optional<RoutedClient> tryOpen(String configId) {
        if (!StringUtils.hasText(configId)) {
            return Optional.empty();
        }
        try {
            ChatClient client = llmClientFactory.getChatClient(configId);
            LlmConfigRecord cfg = llmClientFactory.getConfig(configId);
            List<String> ordered = orderedConfigIds();
            boolean fallback = !ordered.isEmpty() && !configId.equals(ordered.get(0));
            return Optional.of(new RoutedClient(configId, cfg, client, fallback));
        } catch (WujiException ex) {
            if (ex.getErrorCode() == ErrorCode.MODEL_UNAVAILABLE || ex.getErrorCode() == ErrorCode.NOT_FOUND) {
                log.warn("skip llm config {}: {}", configId, ex.getMessage());
                return Optional.empty();
            }
            throw ex;
        }
    }

    /**
     * 打开有序列表中从 fromIndex 起第一个可用客户端。
     *
     * @param fromIndex 起始下标
     * @return 客户端与其在列表中的下标
     */
    public Optional<IndexedClient> openFrom(int fromIndex) {
        List<String> ids = orderedConfigIds();
        for (int i = Math.max(0, fromIndex); i < ids.size(); i++) {
            Optional<RoutedClient> opened = tryOpen(ids.get(i));
            if (opened.isPresent()) {
                return Optional.of(new IndexedClient(i, opened.get()));
            }
        }
        return Optional.empty();
    }

    /**
     * 非流式调用：重试 + 主备切换，每次尝试写审计。
     *
     * @param messages 入模消息
     * @param audit    审计上下文
     * @return 文本与实际 config
     */
    public RoutedResult<String> callContent(List<Message> messages, AuditContext audit) {
        return execute(audit, client -> {
            ChatResponse response = client.chatClient().prompt().messages(messages).call().chatResponse();
            Integer[] tokens = TokenUsageExtractor.fromChatResponse(response);
            LlmUsageHolder.set(tokens[0], tokens[1]);
            String content = response == null || response.getResult() == null
                    || response.getResult().getOutput() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return content == null ? "" : content;
        });
    }

    /**
     * 通用非流式执行。
     *
     * @param audit 审计上下文
     * @param call  业务调用
     * @param <T>   返回类型
     * @return 路由结果
     */
    public <T> RoutedResult<T> execute(AuditContext audit, Function<RoutedClient, T> call) {
        List<String> ids = orderedConfigIds();
        if (ids.isEmpty()) {
            throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "未配置 primary-config-id");
        }
        Throwable last = null;
        int maxAttempts = Math.max(1, modelProperties.getRetry().getMaxAttempts());
        Duration backoff = modelProperties.getRetry().getBackoff() == null
                ? Duration.ofSeconds(1) : modelProperties.getRetry().getBackoff();

        for (int configIndex = 0; configIndex < ids.size(); configIndex++) {
            String configId = ids.get(configIndex);
            Optional<RoutedClient> opened = tryOpen(configId);
            if (opened.isEmpty()) {
                continue;
            }
            if (configIndex > 0) {
                agentTelemetry.countFailover(ids.get(configIndex - 1), configId);
            }
            RoutedClient routed = opened.get();
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                long start = System.currentTimeMillis();
                try {
                    T value = agentTelemetry.observe("llm.call", new String[]{
                            "modelId", routed.configId(),
                            "attempt", String.valueOf(attempt),
                            "is_fallback", String.valueOf(routed.fallback())
                    }, () -> call.apply(routed));
                    llmCallAuditor.record(successAudit(audit, routed, attempt, (int) (System.currentTimeMillis() - start), value));
                    return new RoutedResult<>(value, routed);
                } catch (Throwable ex) {
                    last = ex;
                    LlmUsageHolder.clear();
                    String errCode = mapErrorCode(ex).getCode();
                    llmCallAuditor.record(failedAudit(audit, routed, attempt, (int) (System.currentTimeMillis() - start),
                            errCode, ex.getMessage()));
                    boolean retryable = isRetryable(ex);
                    log.warn("LLM call failed configId={} attempt={}/{} retryable={} msg={}",
                            configId, attempt, maxAttempts, retryable, ex.toString());
                    if (!retryable) {
                        throw wrap(ex);
                    }
                    if (attempt < maxAttempts) {
                        sleep(backoff.multipliedBy(attempt));
                    }
                }
            }
            // 同配置重试耗尽，若可 failover 则下一配置
            if (last != null && !isFailoverWorthy(last)) {
                throw wrap(last);
            }
        }
        if (last != null) {
            ErrorCode code = mapErrorCode(last);
            if (code == ErrorCode.MODEL_RATE_LIMITED) {
                throw new WujiException(ErrorCode.MODEL_RATE_LIMITED, "主备模型均限流或重试耗尽", last);
            }
            if (code == ErrorCode.MODEL_TIMEOUT) {
                throw new WujiException(ErrorCode.MODEL_TIMEOUT, "主备模型均超时", last);
            }
            throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "主备模型均不可用", last);
        }
        throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "无可用 LLM 配置");
    }

    /**
     * 异常是否可同配置重试。
     *
     * @param t 异常
     * @return true 可重试
     */
    public boolean isRetryable(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof WujiException w) {
            return switch (w.getErrorCode()) {
                case MODEL_RATE_LIMITED, MODEL_TIMEOUT, MODEL_UNAVAILABLE, INTERNAL_ERROR -> true;
                case BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND -> false;
                default -> false;
            };
        }
        if (hasCause(t, TimeoutException.class)) {
            return true;
        }
        Integer status = extractHttpStatus(t);
        if (status != null) {
            if (modelProperties.getRateLimitCodes() != null && modelProperties.getRateLimitCodes().contains(status)) {
                return true;
            }
            return status >= 500 || status == 408;
        }
        String msg = String.valueOf(t.getMessage()).toLowerCase();
        return msg.contains("429") || msg.contains("rate limit") || msg.contains("timeout")
                || msg.contains("timed out") || msg.contains("connection") || msg.contains("unavailable");
    }

    /**
     * 是否值得切换备用配置。
     *
     * @param t 异常
     * @return true 可 failover
     */
    public boolean isFailoverWorthy(Throwable t) {
        return isRetryable(t);
    }

    /**
     * 映射错误码。
     *
     * @param t 异常
     * @return ErrorCode
     */
    public ErrorCode mapErrorCode(Throwable t) {
        if (t instanceof WujiException w) {
            return w.getErrorCode();
        }
        Integer status = extractHttpStatus(t);
        if (status != null && modelProperties.getRateLimitCodes() != null
                && modelProperties.getRateLimitCodes().contains(status)) {
            return ErrorCode.MODEL_RATE_LIMITED;
        }
        if (hasCause(t, TimeoutException.class)
                || String.valueOf(t.getMessage()).toLowerCase().contains("timeout")) {
            return ErrorCode.MODEL_TIMEOUT;
        }
        return ErrorCode.MODEL_UNAVAILABLE;
    }

    private LlmCallAuditor.AuditParams successAudit(AuditContext audit, RoutedClient routed,
                                                    int attempt, int latencyMs, Object value) {
        String content = value instanceof String s ? s : String.valueOf(value);
        LlmUsageHolder.Usage usage = LlmUsageHolder.getAndClear();
        return new LlmCallAuditor.AuditParams(
                audit.traceId(),
                audit.conversationId(),
                audit.messageId(),
                audit.userId(),
                routed.configId(),
                routed.config().getProvider(),
                attempt,
                routed.fallback(),
                "SUCCESS",
                null,
                latencyMs,
                usage.promptTokens(),
                usage.completionTokens(),
                Map.of(
                        "system", audit.systemPrompt(),
                        "user", audit.userPrompt(),
                        "model", routed.config().getModel()
                ),
                Map.of("content", content == null ? "" : content)
        );
    }

    private LlmCallAuditor.AuditParams failedAudit(AuditContext audit, RoutedClient routed,
                                                   int attempt, int latencyMs, String errCode, String msg) {
        return new LlmCallAuditor.AuditParams(
                audit.traceId(),
                audit.conversationId(),
                audit.messageId(),
                audit.userId(),
                routed.configId(),
                routed.config().getProvider(),
                attempt,
                routed.fallback(),
                "FAILED",
                errCode,
                latencyMs,
                null,
                null,
                Map.of(
                        "system", audit.systemPrompt(),
                        "user", audit.userPrompt(),
                        "model", routed.config().getModel()
                ),
                Map.of("error", msg == null ? "" : msg)
        );
    }

    private static void sleep(Duration d) {
        if (d == null || d.isZero() || d.isNegative()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static WujiException wrap(Throwable ex) {
        if (ex instanceof WujiException w) {
            return w;
        }
        return new WujiException(ErrorCode.MODEL_UNAVAILABLE,
                ex.getMessage() == null ? ErrorCode.MODEL_UNAVAILABLE.getMessage() : ex.getMessage(), ex);
    }

    private static Integer extractHttpStatus(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof WebClientResponseException w) {
                return w.getStatusCode().value();
            }
            cur = cur.getCause();
        }
        return null;
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        Throwable cur = t;
        while (cur != null) {
            if (type.isInstance(cur)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 已打开的路由客户端。
     *
     * @param configId   配置键
     * @param config     配置行
     * @param chatClient 客户端
     * @param fallback   是否备用
     * @author liudy
     */
    public record RoutedClient(
            String configId,
            LlmConfigRecord config,
            ChatClient chatClient,
            boolean fallback
    ) {
    }

    /**
     * 带列表下标的客户端。
     *
     * @param index  在 orderedConfigIds 中的下标
     * @param client 客户端
     * @author liudy
     */
    public record IndexedClient(int index, RoutedClient client) {
    }

    /**
     * 非流式路由结果。
     *
     * @param value  业务值
     * @param client 实际使用的客户端
     * @param <T>    类型
     * @author liudy
     */
    public record RoutedResult<T>(T value, RoutedClient client) {
        public String configId() {
            return client.configId();
        }
    }

    /**
     * 审计上下文。
     *
     * @param traceId        追踪
     * @param conversationId 会话
     * @param messageId      助手消息
     * @param userId         用户
     * @param systemPrompt   系统提示
     * @param userPrompt     用户提示
     * @author liudy
     */
    public record AuditContext(
            String traceId,
            String conversationId,
            String messageId,
            String userId,
            String systemPrompt,
            String userPrompt
    ) {
    }
}
