package com.wuji.assistant.agent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 查询改写失败时退回原问题。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class RagQueryRewriteOrchestratorTest {

    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private ModelRouter modelRouter;

    private WujiRagProperties ragProperties;
    private RagQueryRewriteOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        ragProperties = new WujiRagProperties();
        ragProperties.getQueryRewrite().setEnabled(true);
        ragProperties.getQueryRewrite().setTimeout(Duration.ofSeconds(2));
        ragProperties.getQueryRewrite().setMaxQueries(3);
        orchestrator = new RagQueryRewriteOrchestrator(
                ragProperties, promptTemplateService, modelRouter, new ObjectMapper());
        when(promptTemplateService.loadAndRender(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void modelFailureKeepsOriginalQuery() throws Exception {
        when(modelRouter.callContent(anyList(), any())).thenThrow(new IllegalStateException("boom"));

        RagQueryRewrite rewritten = orchestrator.rewrite("u1", "c1", "车厘子怎么保存");

        assertFalse(rewritten.rewritten());
        assertFalse(rewritten.relational());
        assertEquals(List.of("车厘子怎么保存"), rewritten.queries());
    }

    @Test
    void parsesSynonymsAndRelationFlag() throws Exception {
        when(modelRouter.callContent(anyList(), any())).thenReturn(new ModelRouter.RoutedResult<>(
                "{\"queries\":[\"樱桃保存\",\"车厘子冷藏\"],\"relational\":true}", null));

        RagQueryRewrite rewritten = orchestrator.rewrite("u1", "c1", "车厘子怎么保存");

        assertTrue(rewritten.rewritten());
        assertTrue(rewritten.relational());
        assertEquals(List.of("车厘子怎么保存", "樱桃保存", "车厘子冷藏"), rewritten.queries());
    }

    @Test
    void garbageJsonKeepsOriginal() throws Exception {
        when(modelRouter.callContent(anyList(), any())).thenReturn(new ModelRouter.RoutedResult<>("不是 json", null));

        RagQueryRewrite rewritten = orchestrator.rewrite("u1", "c1", "原问题");

        assertFalse(rewritten.rewritten());
        assertEquals(List.of("原问题"), rewritten.queries());
    }
}
