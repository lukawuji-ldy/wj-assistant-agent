package com.wuji.assistant.vta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.vta.graph.VtaGraphKeys;
import com.wuji.assistant.vta.graph.VtaMergePartialNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VtaAnalysisExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void partialWhenOneNodeJsonInvalid() {
        VtaAnalysisExecutor executor = newExecutor(messageId -> switch (messageId) {
            case "customerTag" -> "NOT_JSON";
            case "salesTag" -> "{\"销售标签\":\"x\",\"标签命中原因\":\"ok\"}";
            case "callSummary" -> "{\"总结文本\":\"sum\"}";
            case "intentScore" -> "{\"意向度\":\"20\",\"意向度判断依据\":\"good\"}";
            case "aggregate" -> aggregateJson();
            default -> "{}";
        });

        CopyOnWriteArrayList<String> doneEvents = new CopyOnWriteArrayList<>();
        VtaAnalysisResult result = executor.execute(
                "job_1", "user_1", "transcript", "trace_1",
                (nodeName, parsedJson) -> doneEvents.add(nodeName));

        assertEquals(VtaAnalysisStatus.PARTIAL, result.status());
        assertTrue(result.customerTags().hasNonNull("error"));
        assertTrue(doneEvents.containsAll(List.of(
                "customerTag", "salesTag", "callSummary", "intentScore", "aggregate")));
        JsonNode failures = result.rawNodeOutputs().path(VtaGraphKeys.PARTIAL_FAILURE);
        assertTrue(failures.path("any").asBoolean());
        assertTrue(failures.path("nodes").toString().contains("customerTag"));
    }

    @Test
    void nodeExceptionDoesNotAbortSiblings() {
        VtaAnalysisExecutor executor = newExecutor(messageId -> {
            if ("customerTag".equals(messageId)) {
                throw new RuntimeException("boom-customer");
            }
            return switch (messageId) {
                case "salesTag" -> "{\"销售标签\":\"x\",\"标签命中原因\":\"ok\"}";
                case "callSummary" -> "{\"总结文本\":\"sum\"}";
                case "intentScore" -> "{\"意向度\":\"20\",\"意向度判断依据\":\"good\"}";
                case "aggregate" -> aggregateJson();
                default -> "{}";
            };
        });

        CopyOnWriteArrayList<String> doneEvents = new CopyOnWriteArrayList<>();
        VtaAnalysisResult result = executor.execute(
                "job_2", "user_1", "transcript", "trace_1",
                (nodeName, parsedJson) -> doneEvents.add(nodeName));

        assertEquals(VtaAnalysisStatus.PARTIAL, result.status());
        assertTrue(result.customerTags().hasNonNull("error"));
        assertEquals("boom-customer", result.customerTags().path("detail").asText());
        assertEquals("x", result.salesTags().path("销售标签").asText());
        assertEquals("sum", result.summary().path("总结文本").asText());
        assertTrue(doneEvents.containsAll(List.of(
                "customerTag", "salesTag", "callSummary", "intentScore", "aggregate")));
    }

    @Test
    void nodeTimeout_emitsErrorJsonAndDoesNotAbortSiblings() {
        VtaAnalysisExecutor executor = newExecutor(messageId -> {
            if ("customerTag".equals(messageId)) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return switch (messageId) {
                case "customerTag" -> "{\"客户标签\":\"late\",\"标签命中原因\":\"slow\"}";
                case "salesTag" -> "{\"销售标签\":\"x\",\"标签命中原因\":\"ok\"}";
                case "callSummary" -> "{\"总结文本\":\"sum\"}";
                case "intentScore" -> "{\"意向度\":\"20\",\"意向度判断依据\":\"good\"}";
                case "aggregate" -> aggregateJson();
                default -> "{}";
            };
        }, java.time.Duration.ofSeconds(5), java.time.Duration.ofMillis(200));

        CopyOnWriteArrayList<String> doneEvents = new CopyOnWriteArrayList<>();
        long started = System.nanoTime();
        VtaAnalysisResult result = executor.execute(
                "job_timeout_node", "user_1", "transcript", "trace_1",
                (nodeName, parsedJson) -> doneEvents.add(nodeName));
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals(VtaAnalysisStatus.PARTIAL, result.status());
        assertEquals("VTA_GRAPH_TIMEOUT", result.customerTags().path("error").asText());
        assertEquals("x", result.salesTags().path("销售标签").asText());
        assertTrue(elapsedMs < 1_500, "node timeout should not wait for hung LLM, elapsedMs=" + elapsedMs);
        assertTrue(doneEvents.containsAll(List.of(
                "customerTag", "salesTag", "callSummary", "intentScore", "aggregate")));
    }

    @Test
    void mergePartialWritesFailureList() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        VtaMergePartialNode node = new VtaMergePartialNode(mapper);
        com.alibaba.cloud.ai.graph.OverAllState state = mock(com.alibaba.cloud.ai.graph.OverAllState.class);
        JsonNode err = mapper.readTree("{\"error\":\"VTA_JSON_PARSE_FAILED\"}");
        JsonNode okSales = mapper.readTree("{\"销售标签\":\"x\",\"标签命中原因\":\"ok\"}");
        JsonNode okSum = mapper.readTree("{\"总结文本\":\"sum\"}");
        JsonNode okIntent = mapper.readTree("{\"意向度\":\"20\",\"意向度判断依据\":\"good\"}");
        when(state.value(VtaGraphKeys.CUSTOMER_TAG)).thenReturn(java.util.Optional.of(err));
        when(state.value(VtaGraphKeys.SALES_TAG)).thenReturn(java.util.Optional.of(okSales));
        when(state.value(VtaGraphKeys.CALL_SUMMARY)).thenReturn(java.util.Optional.of(okSum));
        when(state.value(VtaGraphKeys.INTENT_SCORE)).thenReturn(java.util.Optional.of(okIntent));

        var out = node.apply(state);
        JsonNode failure = (JsonNode) out.get(VtaGraphKeys.PARTIAL_FAILURE);
        assertTrue(failure.path("any").asBoolean());
        assertEquals(1, failure.path("nodes").size());
        assertEquals("customerTag", failure.path("nodes").get(0).asText());
    }

    private VtaAnalysisExecutor newExecutor(java.util.function.Function<String, String> byMessageId) {
        return newExecutor(byMessageId, java.time.Duration.ofSeconds(120), java.time.Duration.ofSeconds(60));
    }

    private VtaAnalysisExecutor newExecutor(java.util.function.Function<String, String> byMessageId,
                                            java.time.Duration jobTimeout,
                                            java.time.Duration nodeTimeout) {
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        ModelRouter modelRouter = mock(ModelRouter.class);

        when(promptTemplateService.loadActiveContent(VtaPromptCodes.CUSTOMER_TAG_SYSTEM)).thenReturn("sys customer");
        when(promptTemplateService.loadActiveContent(VtaPromptCodes.SALES_TAG_SYSTEM)).thenReturn("sys sales");
        when(promptTemplateService.loadActiveContent(VtaPromptCodes.CALL_SUMMARY_SYSTEM)).thenReturn("sys summary");
        when(promptTemplateService.loadActiveContent(VtaPromptCodes.INTENT_SCORE_SYSTEM)).thenReturn("sys intent");
        when(promptTemplateService.loadActiveContent(VtaPromptCodes.AGGREGATE_SYSTEM)).thenReturn("sys aggregate");
        when(promptTemplateService.loadActiveContent(VtaPromptCodes.TRANSCRIPT_USER)).thenReturn("通话:{{transcript}}");
        when(promptTemplateService.loadAndRender(eq(VtaPromptCodes.TRANSCRIPT_USER), any(), anyString()))
                .thenReturn("transcript");

        LlmConfigRecord cfg = new LlmConfigRecord();
        cfg.setConfigId("llm_primary");
        cfg.setProvider("openai_compatible");
        cfg.setModelKind(LlmConfigRecord.KIND_CHAT);
        cfg.setModel("gpt-test");
        ChatClient dummyClient = mock(ChatClient.class);
        ModelRouter.RoutedClient dummyRoutedClient = new ModelRouter.RoutedClient(
                "llm_primary", cfg, dummyClient, false);

        when(modelRouter.callContent(anyList(), any()))
                .thenAnswer(inv -> {
                    ModelRouter.AuditContext audit = inv.getArgument(1);
                    String content = byMessageId.apply(audit.messageId());
                    return new ModelRouter.RoutedResult<>(content, dummyRoutedClient);
                });

        return new VtaAnalysisExecutor(
                promptTemplateService, modelRouter, objectMapper, 20_000, jobTimeout, nodeTimeout);
    }

    private static String aggregateJson() {
        return """
                {
                  "aggregateText":"ok",
                  "raw":{
                    "customerTag": {"客户标签":"a","标签命中原因":"ok"},
                    "salesTag": {"销售标签":"x","标签命中原因":"ok"},
                    "callSummary": {"总结文本":"sum"},
                    "intentScore": {"意向度":"20","意向度判断依据":"good"}
                  }
                }
                """;
    }
}
