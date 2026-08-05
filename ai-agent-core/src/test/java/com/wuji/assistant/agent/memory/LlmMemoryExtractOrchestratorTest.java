package com.wuji.assistant.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.memory.extract.MemoryAction;
import com.wuji.assistant.memory.extract.MemoryActionItem;
import com.wuji.assistant.memory.extract.MemoryExtractService;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LlmMemoryExtractOrchestrator：多 Action + hybrid 降级。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class LlmMemoryExtractOrchestratorTest {

    @Mock
    private MemoryExtractService memoryExtractService;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private ModelRouter modelRouter;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private ObjectProvider<EmbeddingClient> embeddingClientProvider;

    private WujiMemoryProperties memoryProperties;
    private LlmMemoryExtractOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        memoryProperties = new WujiMemoryProperties();
        memoryProperties.getExtract().setEnabled(true);
        memoryProperties.getExtract().setMode("hybrid");
        memoryProperties.getExtract().setTimeout(Duration.ofSeconds(5));
        memoryProperties.getExtract().setMinConfidence(0.55);
        lenient().when(embeddingClientProvider.getIfAvailable()).thenReturn(embeddingClient);
        orchestrator = new LlmMemoryExtractOrchestrator(
                memoryProperties,
                memoryExtractService,
                promptTemplateService,
                modelRouter,
                new ObjectMapper(),
                embeddingClientProvider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void hybrid_llmSuccess_appliesMultiActionsWithEmbed() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        String json = """
                {"actions":[
                  {"action":"UPDATE","type":"PROFILE","key":"hometown","newValue":"山西大同","confidence":0.9},
                  {"action":"UPDATE","type":"PROFILE","key":"residence","newValue":"河北燕郊","confidence":0.88},
                  {"action":"INSERT","type":"SEMANTIC","content":"籍贯山西大同现居河北燕郊","confidence":0.8,"importance":0.7}
                ]}
                """;
        when(modelRouter.callContent(anyList(), any()))
                .thenReturn(new ModelRouter.RoutedResult<>(json, null));
        when(embeddingClient.available()).thenReturn(true);
        float[] vec = new float[1536];
        vec[0] = 0.1f;
        when(embeddingClient.embed(anyString())).thenReturn(vec);

        orchestrator.extract("c1", "m1", "u1", "用户说籍贯", "助手确认");

        ArgumentCaptor<List<MemoryActionItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(memoryExtractService).applyActions(eq("c1"), eq("m1"), eq("u1"), captor.capture());
        verify(memoryExtractService, never()).extract(anyString(), anyString(), anyString(), anyString(), anyString());
        List<MemoryActionItem> applied = captor.getValue();
        assertEquals(3, applied.size());
        assertEquals("hometown", applied.get(0).memoryKey());
        assertEquals(MemoryAction.INSERT, applied.get(2).action());
        assertEquals(true, applied.get(2).embeddingVectorLiteral().startsWith("["));
        verify(embeddingClient).embed("籍贯山西大同现居河北燕郊");
    }

    @Test
    void hybrid_llmThrows_fallsBackToRule() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callContent(anyList(), any())).thenThrow(new RuntimeException("boom"));

        orchestrator.extract("c1", "m1", "u1", "我叫张三", "好的");

        verify(memoryExtractService).extract("c1", "m1", "u1", "我叫张三", "好的");
        verify(memoryExtractService, never()).applyActions(anyString(), anyString(), anyString(), anyList());
        verify(memoryExtractService, never()).recordFailed(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void llmMode_failure_recordsFailed_noRule() {
        memoryProperties.getExtract().setMode("llm");
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callContent(anyList(), any())).thenThrow(new RuntimeException("boom"));

        orchestrator.extract("c1", "m1", "u1", "我喜欢蓝色", "好的");

        verify(memoryExtractService).recordFailed(eq("c1"), eq("m1"), eq("u1"), anyString());
        verify(memoryExtractService, never()).extract(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ruleMode_skipsLlm() {
        memoryProperties.getExtract().setMode("rule");
        orchestrator.extract("c1", "m1", "u1", "我叫张三", "好的");
        verify(memoryExtractService).extract("c1", "m1", "u1", "我叫张三", "好的");
        verify(modelRouter, never()).callContent(anyList(), any());
    }
}
