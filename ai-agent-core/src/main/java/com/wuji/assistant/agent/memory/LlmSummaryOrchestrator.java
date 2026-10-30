package com.wuji.assistant.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.observability.ChatMdc;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.scheduler.SafeTaskScheduler;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.common.util.PostgresText;
import com.wuji.assistant.memory.SummaryService;
import com.wuji.assistant.memory.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 会话摘要编排：优先 LLM 滚动合并，超时或解析失败则降级 {@link SummaryService} 规则摘要。
 *
 * @author liudy
 */
@Service
public class LlmSummaryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LlmSummaryOrchestrator.class);

    private static final int MESSAGE_CHARS = 800;

    static final String FALLBACK_SYSTEM = """
            你是会话摘要器。只输出 JSON，不要 markdown。
            schema：{"goal":"","facts":[],"decisions":[],"pending_tasks":[]}
            将旧摘要与待压缩消息滚动合并。保留目标、关键事实、已做决策、未完成任务；删除闲聊。
            """;

    static final String FALLBACK_USER = """
            旧摘要:
            {{old_summary}}

            待压缩消息:
            {{messages}}
            """;

    private final WujiMemoryProperties memoryProperties;
    private final SummaryService summaryService;
    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;
    private final ConversationCompressGate compressGate = new ConversationCompressGate();

    public LlmSummaryOrchestrator(WujiMemoryProperties memoryProperties,
                                  SummaryService summaryService,
                                  PromptTemplateService promptTemplateService,
                                  ModelRouter modelRouter,
                                  ObjectMapper objectMapper) {
        this.memoryProperties = memoryProperties;
        this.summaryService = summaryService;
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * 按短记忆配置压缩。LLM 关闭时只走规则。失败只打日志。
     *
     * @param userId         用户
     * @param conversationId 会话
     */
    public void compress(String userId, String conversationId) {
        compress(userId, conversationId, -1);
    }

    /**
     * @param tokenThreshold 覆盖配置里的 token 阈值；负数表示仍用配置
     */
    public void compress(String userId, String conversationId, int tokenThreshold) {
        if (!compressGate.tryAcquire(conversationId)) {
            log.info("summary compress skipped, already running conversationId={}", conversationId);
            return;
        }
        try {
            doCompress(userId, conversationId, tokenThreshold);
        } finally {
            compressGate.release(conversationId);
        }
    }

    private void doCompress(String userId, String conversationId, int tokenThreshold) {
        WujiMemoryProperties.ShortTerm cfg = memoryProperties.getShort();
        if (cfg == null) {
            return;
        }
        int messageThreshold = cfg.getCompressMessageThreshold();
        int keep = cfg.getKeepRecentMessages();
        int threshold = tokenThreshold >= 0 ? tokenThreshold : cfg.getCompressTokenThreshold();
        if (!cfg.isSummaryLlmEnabled()) {
            summaryService.compressIfNeeded(userId, conversationId, messageThreshold, keep, threshold);
            return;
        }
        try {
            Optional<SummaryService.CompressionBatch> prepared = summaryService.prepare(
                    userId, conversationId, messageThreshold, keep, threshold);
            if (prepared.isEmpty()) {
                return;
            }
            SummaryService.CompressionBatch batch = prepared.get();
            String summaryJson = summarizeOrRules(userId, batch, cfg, cfg.getSummaryTimeout());
            summaryService.commitSummary(batch, summaryJson);
            log.info("summary compressed conversationId={} untilMsg={} messages={}",
                    conversationId, batch.lastCovered().getMessageId(), batch.toCompress().size());
        } catch (Exception e) {
            log.warn("summary orchestrator failed conversationId={}: {}", conversationId, e.toString());
        }
    }

    /**
     * 同步紧急压缩。同一会话已有任务在跑时返回 false，不阻塞。
     *
     * @param llmTimeout LLM 超时；空则用 {@code summary-timeout}
     * @return 本次是否执行了压缩
     */
    public boolean compressUrgent(String userId, String conversationId, Duration llmTimeout) {
        if (!compressGate.tryAcquire(conversationId)) {
            log.info("urgent compress skipped, already running conversationId={}", conversationId);
            return false;
        }
        try {
            doUrgent(userId, conversationId, llmTimeout);
            return true;
        } finally {
            compressGate.release(conversationId);
        }
    }

    public void compressUrgent(String userId, String conversationId) {
        compressUrgent(userId, conversationId, null);
    }

    /**
     * 异步紧急压缩。已有任务在跑或队列满时不再提交。
     *
     * @return 是否已提交
     */
    public boolean submitUrgent(Scheduler scheduler, String userId, String conversationId) {
        if (scheduler == null || !compressGate.tryAcquire(conversationId)) {
            return false;
        }
        Disposable disposable = SafeTaskScheduler.submit(scheduler, () -> {
            try {
                doUrgent(userId, conversationId, null);
            } finally {
                compressGate.release(conversationId);
            }
        }, "urgent-compress:" + conversationId, Duration.ofMinutes(5));
        if (disposable == null) {
            compressGate.release(conversationId);
            return false;
        }
        return true;
    }

    private void doUrgent(String userId, String conversationId, Duration llmTimeout) {
        WujiMemoryProperties.ShortTerm cfg = memoryProperties.getShort();
        if (cfg == null) {
            return;
        }
        try {
            Optional<SummaryService.CompressionBatch> prepared = summaryService.prepareForced(
                    userId, conversationId, cfg.getKeepRecentMessages());
            if (prepared.isEmpty()) {
                return;
            }
            SummaryService.CompressionBatch batch = prepared.get();
            String summaryJson = cfg.isSummaryLlmEnabled()
                    ? summarizeOrRules(userId, batch, cfg, llmTimeout == null ? cfg.getSummaryTimeout() : llmTimeout)
                    : summaryService.buildRollingSummary(batch.oldSummaryJson(), batch.toCompress());
            summaryService.commitSummary(batch, summaryJson);
            log.info("summary urgent compressed conversationId={} untilMsg={} messages={}",
                    conversationId, batch.lastCovered().getMessageId(), batch.toCompress().size());
        } catch (Exception e) {
            log.warn("urgent summary failed conversationId={}: {}", conversationId, e.toString());
        }
    }

    private String summarizeOrRules(String userId, SummaryService.CompressionBatch batch,
                                    WujiMemoryProperties.ShortTerm cfg, Duration timeout) throws Exception {
        try {
            return summarizeByLlm(userId, batch, cfg, timeout);
        } catch (Exception e) {
            log.warn("LLM summary failed conversationId={}: {}", batch.conversationId(), e.toString());
            return summaryService.buildRollingSummary(batch.oldSummaryJson(), batch.toCompress());
        }
    }

    private String summarizeByLlm(String userId, SummaryService.CompressionBatch batch,
                                  WujiMemoryProperties.ShortTerm cfg, Duration timeout) throws Exception {
        String oldSummary = batch.oldSummaryJson() == null ? "" : batch.oldSummaryJson();
        String messages = formatMessages(batch.toCompress());
        String systemPrompt = promptTemplateService.loadAndRender(
                cfg.getSummarySystemPromptCode(), Map.of(), FALLBACK_SYSTEM);
        String userPrompt = promptTemplateService.loadAndRender(
                cfg.getSummaryUserPromptCode(),
                Map.of("old_summary", oldSummary, "messages", messages),
                FALLBACK_USER);
        String traceId = MDC.get(ChatMdc.TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = IdGenerator.nextBizId("tr_");
        }
        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                traceId,
                batch.conversationId(),
                batch.lastCovered().getMessageId(),
                userId,
                "CHAT",
                null,
                systemPrompt,
                userPrompt);
        List<Message> prompt = List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
        Duration limit = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(20) : timeout;
        String raw = callWithTimeout(() -> modelRouter.callContent(prompt, audit).value(), limit);
        return canonicalSummary(raw, objectMapper);
    }

    static String formatMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        if (messages == null) {
            return "";
        }
        for (ChatMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String snippet = PostgresText.sanitize(message.getContent()).trim();
            if (snippet.isBlank()) {
                continue;
            }
            if (snippet.length() > MESSAGE_CHARS) {
                snippet = snippet.substring(0, MESSAGE_CHARS);
            }
            String role = message.getRole() == null ? "user" : message.getRole();
            sb.append(role).append(": ").append(snippet).append('\n');
        }
        return sb.toString();
    }

    /**
     * 把模型输出收成固定四字段 JSON。缺字段或非对象则抛异常，交给规则降级。
     *
     * @param raw    模型原文
     * @param mapper Jackson
     * @return 可落库 JSON
     */
    static String canonicalSummary(String raw, ObjectMapper mapper) throws Exception {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("empty summary response");
        }
        String json = unwrapJson(raw.trim());
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("summary object required");
        }
        if (!root.has("goal") && !root.has("facts") && !root.has("decisions") && !root.has("pending_tasks")) {
            throw new IllegalArgumentException("summary fields missing");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("goal", root.has("goal") && !root.get("goal").isNull() ? root.get("goal").asText("") : "");
        out.put("facts", textList(root.get("facts")));
        out.put("decisions", textList(root.get("decisions")));
        out.put("pending_tasks", textList(root.get("pending_tasks")));
        return PostgresText.sanitizeJson(mapper.writeValueAsString(out));
    }

    private static List<String> textList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return list;
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            String text = PostgresText.sanitize(item.asText("")).trim();
            if (StringUtils.hasText(text)) {
                list.add(text);
            }
        }
        return list;
    }

    static String unwrapJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
        }
        return s.trim();
    }

    private static <T> T callWithTimeout(Callable<T> call, Duration timeout) throws Exception {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return call.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        try {
            return future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re && re.getCause() instanceof Exception nested) {
                throw nested;
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}
