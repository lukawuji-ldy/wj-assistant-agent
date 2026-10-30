package com.wuji.assistant.agent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.observability.ChatMdc;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 检索前查询改写。关闭、超时或解析失败时使用原问题，不阻断检索。
 *
 * @author liudy
 */
@Service
public class RagQueryRewriteOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RagQueryRewriteOrchestrator.class);

    static final String FALLBACK_SYSTEM = """
            你是检索查询改写器。只输出 JSON，不要 markdown。
            schema：{"queries":["同义或更具体的检索问句"],"relational":false}
            queries 给出 2 到 3 条，覆盖同义词和上位概念（例如车厘子与樱桃），不要回答问题本身。
            若问题在问两个事物的关系、对比或区别，relational 设为 true。
            """;

    static final String FALLBACK_USER = """
            原问题:
            {{query}}
            """;

    static final String FALLBACK_RELATION = """
            这是关系或对比类问题。请按「实体、关系、依据」三段回答，每段只使用已召回片段，不要补充知识库之外的事实。
            """;

    private final WujiRagProperties ragProperties;
    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public RagQueryRewriteOrchestrator(WujiRagProperties ragProperties,
                                       PromptTemplateService promptTemplateService,
                                       ModelRouter modelRouter,
                                       ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * @param userId         用户，可空
     * @param conversationId 会话，可空
     * @param query          原问题
     * @return 检索用问句
     */
    public RagQueryRewrite rewrite(String userId, String conversationId, String query) {
        if (!StringUtils.hasText(query)) {
            return RagQueryRewrite.original(query);
        }
        WujiRagProperties.QueryRewrite cfg = ragProperties.getQueryRewrite();
        if (cfg == null || !cfg.isEnabled()) {
            return RagQueryRewrite.original(query);
        }
        try {
            String systemPrompt = promptTemplateService.loadAndRender(
                    cfg.getSystemPromptCode(), Map.of(), FALLBACK_SYSTEM);
            String userPrompt = promptTemplateService.loadAndRender(
                    cfg.getUserPromptCode(), Map.of("query", query.trim()), FALLBACK_USER);
            String traceId = MDC.get(ChatMdc.TRACE_ID);
            if (!StringUtils.hasText(traceId)) {
                traceId = IdGenerator.nextBizId("tr_");
            }
            ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                    traceId,
                    conversationId,
                    null,
                    userId,
                    "RAG",
                    null,
                    systemPrompt,
                    userPrompt);
            List<Message> prompt = List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
            Duration timeout = cfg.getTimeout() == null ? Duration.ofSeconds(8) : cfg.getTimeout();
            String raw = callWithTimeout(() -> modelRouter.callContent(prompt, audit).value(), timeout);
            RagQueryRewrite parsed = RagQueryRewrite.parse(raw, query, cfg.getMaxQueries(), objectMapper);
            if (!parsed.rewritten()) {
                log.warn("query rewrite unusable, keep original");
            }
            return parsed.queries().isEmpty() ? RagQueryRewrite.original(query) : parsed;
        } catch (Exception e) {
            log.warn("query rewrite failed: {}", e.toString());
            return RagQueryRewrite.original(query);
        }
    }

    /**
     * @return 关系类问题追加到上下文的短说明；提示词缺失时用兜底
     */
    public String relationHint() {
        WujiRagProperties.QueryRewrite cfg = ragProperties.getQueryRewrite();
        String code = cfg == null ? "rag.relation.system" : cfg.getRelationPromptCode();
        return promptTemplateService.loadAndRender(code, Map.of(), FALLBACK_RELATION);
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
            if (cause instanceof RuntimeException runtime && runtime.getCause() instanceof Exception nested) {
                throw nested;
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}
