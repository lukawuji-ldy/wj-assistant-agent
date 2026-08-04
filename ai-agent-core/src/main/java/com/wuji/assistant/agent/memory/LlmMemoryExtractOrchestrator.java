package com.wuji.assistant.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.observability.ChatMdc;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.extract.MemoryAction;
import com.wuji.assistant.memory.extract.MemoryActionGate;
import com.wuji.assistant.memory.extract.MemoryActionItem;
import com.wuji.assistant.memory.extract.MemoryExtractResponseParser;
import com.wuji.assistant.memory.extract.MemoryExtractService;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * L2 Hybrid / LLM Memory Extract 编排：Prompt + Chat + Embedding + applyActions。
 *
 * @author liudy
 */
@Service
public class LlmMemoryExtractOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LlmMemoryExtractOrchestrator.class);

    private static final String FALLBACK_SYSTEM = """
            你是记忆抽取器。只输出 JSON：{"actions":[{action,type,key,newValue,content,confidence,importance,reason}]}。
            PROFILE/PREFERENCE 写键值；叙述写 SEMANTIC+content；无价值写 IGNORE。禁止把整段对话入库。
            条件/未来意图不得写成绝对 tech.language；值须自包含。
            """;

    private static final String FALLBACK_USER = "用户原文:\n{{user_text}}\n\n助手回复:\n{{assistant_text}}";

    private final WujiMemoryProperties memoryProperties;
    private final MemoryExtractService memoryExtractService;
    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;

    public LlmMemoryExtractOrchestrator(WujiMemoryProperties memoryProperties,
                                        MemoryExtractService memoryExtractService,
                                        PromptTemplateService promptTemplateService,
                                        ModelRouter modelRouter,
                                        ObjectMapper objectMapper,
                                        ObjectProvider<EmbeddingClient> embeddingClientProvider) {
        this.memoryProperties = memoryProperties;
        this.memoryExtractService = memoryExtractService;
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
        this.embeddingClientProvider = embeddingClientProvider;
    }

    /**
     * 异步入口（调用方已在弹性线程亦可直接 extract）。
     */
    public void extractAsync(String conversationId, String messageId, String userId,
                             String userText, String assistantText) {
        try {
            extract(conversationId, messageId, userId, userText, assistantText);
        } catch (Exception e) {
            log.warn("memory extract orchestrator failed messageId={}: {}", messageId, e.toString());
        }
    }

    /**
     * 同步提取：按 mode 分流。
     */
    public void extract(String conversationId, String messageId, String userId,
                        String userText, String assistantText) {
        WujiMemoryProperties.Extract cfg = memoryProperties.getExtract();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        String mode = cfg.getMode() == null ? "hybrid" : cfg.getMode();
        if ("rule".equals(mode)) {
            memoryExtractService.extract(conversationId, messageId, userId, userText, assistantText);
            return;
        }
        try {
            extractByLlm(conversationId, messageId, userId, userText, assistantText, cfg);
        } catch (Exception e) {
            log.warn("LLM memory extract failed mode={} messageId={}: {}", mode, messageId, e.toString());
            if ("hybrid".equals(mode)) {
                memoryExtractService.extract(conversationId, messageId, userId, userText, assistantText);
            } else {
                memoryExtractService.recordFailed(conversationId, messageId, userId, e.getMessage());
            }
        }
    }

    private void extractByLlm(String conversationId, String messageId, String userId,
                              String userText, String assistantText,
                              WujiMemoryProperties.Extract cfg) throws Exception {
        String systemCode = cfg.getSystemPromptCode();
        String userCode = cfg.getUserPromptCode();
        String systemPrompt = promptTemplateService.loadAndRender(
                systemCode, Map.of(), FALLBACK_SYSTEM);
        String userPrompt = promptTemplateService.loadAndRender(
                userCode,
                Map.of(
                        "user_text", userText == null ? "" : userText,
                        "assistant_text", assistantText == null ? "" : assistantText
                ),
                FALLBACK_USER);

        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        );
        String traceId = MDC.get(ChatMdc.TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = IdGenerator.nextBizId("tr_");
        }
        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                traceId, conversationId, messageId, userId, systemPrompt, userPrompt);

        Duration timeout = cfg.getTimeout() == null ? Duration.ofSeconds(20) : cfg.getTimeout();
        String raw = callWithTimeout(() -> modelRouter.callContent(messages, audit).value(), timeout);
        List<MemoryActionItem> parsed = MemoryExtractResponseParser.parse(raw, objectMapper);
        List<MemoryActionItem> prepared = prepareActions(parsed, cfg.getMinConfidence());
        memoryExtractService.applyActions(conversationId, messageId, userId, prepared);
    }

    List<MemoryActionItem> prepareActions(List<MemoryActionItem> parsed, double minConfidence) {
        List<MemoryActionItem> out = new ArrayList<>();
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        for (MemoryActionItem item : parsed) {
            if (item == null) {
                continue;
            }
            if (item.action() == MemoryAction.IGNORE) {
                continue;
            }
            if (MemoryActionGate.belowMinConfidence(item, minConfidence)) {
                log.debug("skip low-confidence profile action key={} conf={}",
                        item.memoryKey(), item.confidence());
                continue;
            }
            String type = item.resultType() == null ? "" : item.resultType().trim().toUpperCase();
            if ("SEMANTIC".equals(type)) {
                if (!MemoryActionGate.accept(item)) {
                    continue;
                }
                if (embeddingClient == null || !embeddingClient.available()) {
                    log.warn("skip SEMANTIC: embedding unavailable");
                    continue;
                }
                float[] vector = embeddingClient.embed(item.content());
                if (vector == null || vector.length == 0) {
                    log.warn("skip SEMANTIC: empty embedding");
                    continue;
                }
                out.add(item.withEmbedding(toVectorLiteral(vector)));
            } else if (MemoryActionGate.accept(item)) {
                out.add(item);
            }
        }
        return out;
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

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
