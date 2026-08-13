package com.wuji.assistant.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.observability.ChatMdc;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.retrieve.MemoryRouteDecision;
import com.wuji.assistant.memory.retrieve.MemoryRoutePort;
import com.wuji.assistant.memory.retrieve.MemoryRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 长期记忆 Router：rule 委托；hybrid 时 LLM 判定，失败降级 rule。
 *
 * @author liudy
 */
@Component
@Primary
public class LlmMemoryRouterOrchestrator implements MemoryRoutePort {

    private static final Logger log = LoggerFactory.getLogger(LlmMemoryRouterOrchestrator.class);

    private static final Set<String> ALLOWED_TYPES = Set.of("PROFILE", "PREFERENCE", "SEMANTIC");

    private static final String FALLBACK_SYSTEM = """
            你是长期记忆路由判定器。根据用户本轮问句判断是否需要加载用户长期记忆。
            只输出 JSON：{"needMemory":true|false,"memoryTypes":["PROFILE"|"PREFERENCE"|"SEMANTIC"]}。
            PROFILE=画像身份；PREFERENCE=偏好习惯；SEMANTIC=叙述性经历。
            纯知识问答（如什么是 Redis）needMemory=false 且 memoryTypes=[]。
            """;

    private static final String FALLBACK_USER = "用户问句:\n{{query}}";

    private final WujiMemoryProperties memoryProperties;
    private final MemoryRouter ruleRouter;
    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public LlmMemoryRouterOrchestrator(WujiMemoryProperties memoryProperties,
                                       MemoryRouter ruleRouter,
                                       PromptTemplateService promptTemplateService,
                                       ModelRouter modelRouter,
                                       ObjectMapper objectMapper) {
        this.memoryProperties = memoryProperties;
        this.ruleRouter = ruleRouter;
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    @Override
    public MemoryRouteDecision route(String query) {
        WujiMemoryProperties.Router cfg = memoryProperties.getRouter() == null
                ? new WujiMemoryProperties.Router()
                : memoryProperties.getRouter();
        String mode = cfg.getMode() == null ? "rule" : cfg.getMode();
        if (!"hybrid".equalsIgnoreCase(mode)) {
            return ruleRouter.route(query);
        }
        try {
            return routeByLlm(query, cfg);
        } catch (Exception e) {
            log.warn("memory router hybrid failed, fallback rule: {}", e.toString());
            return ruleRouter.route(query);
        }
    }

    private MemoryRouteDecision routeByLlm(String query, WujiMemoryProperties.Router cfg) throws Exception {
        String systemPrompt = promptTemplateService.loadAndRender(
                cfg.getSystemPromptCode(), Map.of(), FALLBACK_SYSTEM);
        String userPrompt = promptTemplateService.loadAndRender(
                cfg.getUserPromptCode(),
                Map.of("query", query == null ? "" : query),
                FALLBACK_USER);
        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        );
        String traceId = MDC.get(ChatMdc.TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = IdGenerator.nextBizId("tr_");
        }
        String conversationId = MDC.get(ChatMdc.CONVERSATION_ID);
        if (!StringUtils.hasText(conversationId)) {
            conversationId = "memory-router";
        }
        String userId = MDC.get(ChatMdc.USER_ID);
        if (!StringUtils.hasText(userId)) {
            userId = "unknown";
        }
        String messageId = IdGenerator.nextBizId("mr_");
        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                traceId, conversationId, messageId, userId, "CHAT", null, systemPrompt, userPrompt);

        Duration timeout = cfg.getTimeout() == null ? Duration.ofSeconds(2) : cfg.getTimeout();
        String raw = callWithTimeout(() -> modelRouter.callContent(messages, audit).value(), timeout);
        MemoryRouteDecision parsed = parseDecision(raw);
        if (parsed == null) {
            throw new IllegalStateException("unparseable router response");
        }
        log.debug("memory router hybrid decision need={} types={}",
                parsed.needMemory(), parsed.memoryTypes());
        return parsed;
    }

    /**
     * 解析 LLM 路由 JSON；无法解析返回 null。
     */
    MemoryRouteDecision parseDecision(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
            boolean need = root.path("needMemory").asBoolean(false);
            Set<String> types = new LinkedHashSet<>();
            JsonNode arr = root.get("memoryTypes");
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    if (n == null || !n.isTextual()) {
                        continue;
                    }
                    String t = n.asText().trim().toUpperCase(Locale.ROOT);
                    if (ALLOWED_TYPES.contains(t)) {
                        types.add(t);
                    }
                }
            }
            if (!need || types.isEmpty()) {
                return MemoryRouteDecision.skip();
            }
            return MemoryRouteDecision.of(types);
        } catch (Exception e) {
            log.debug("parse memory router json failed: {}", e.toString());
            return null;
        }
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
