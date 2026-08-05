package com.wuji.assistant.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.memory.retrieve.MemoryRouteDecision;
import com.wuji.assistant.memory.retrieve.MemoryRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LlmMemoryRouterOrchestrator：hybrid / rule / 降级。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class LlmMemoryRouterOrchestratorTest {

    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private ModelRouter modelRouter;

    private WujiMemoryProperties memoryProperties;
    private MemoryRouter ruleRouter;
    private LlmMemoryRouterOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        memoryProperties = new WujiMemoryProperties();
        memoryProperties.getRouter().setMode("hybrid");
        memoryProperties.getRouter().setTimeout(Duration.ofSeconds(5));
        ruleRouter = new MemoryRouter();
        orchestrator = new LlmMemoryRouterOrchestrator(
                memoryProperties, ruleRouter, promptTemplateService, modelRouter, new ObjectMapper());
    }

    @Test
    void modeRule_doesNotCallLlm() {
        memoryProperties.getRouter().setMode("rule");
        MemoryRouteDecision d = orchestrator.route("我喜欢什么颜色");
        assertTrue(d.needMemory());
        assertTrue(d.memoryTypes().contains("PREFERENCE"));
        verify(modelRouter, never()).callContent(anyList(), any());
    }

    @Test
    void hybrid_parsesLlmDecision() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callContent(anyList(), any()))
                .thenReturn(new ModelRouter.RoutedResult<>("{\"needMemory\":true,\"memoryTypes\":[\"SEMANTIC\"]}", null));
        MemoryRouteDecision d = orchestrator.route("随便问问");
        assertTrue(d.needMemory());
        assertEquals(Set.of("SEMANTIC"), d.memoryTypes());
    }

    @Test
    void hybrid_badJson_fallsBackToRule() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        when(modelRouter.callContent(anyList(), any()))
                .thenReturn(new ModelRouter.RoutedResult<>("not-json", null));
        MemoryRouteDecision d = orchestrator.route("我喜欢什么颜色");
        assertTrue(d.needMemory());
        assertTrue(d.memoryTypes().contains("PREFERENCE"));
    }

    @Test
    void hybrid_timeout_fallsBackToRule() {
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
        memoryProperties.getRouter().setTimeout(Duration.ofMillis(50));
        when(modelRouter.callContent(anyList(), any())).thenAnswer(inv -> {
            Thread.sleep(500);
            return new ModelRouter.RoutedResult<>("{\"needMemory\":false,\"memoryTypes\":[]}", null);
        });
        MemoryRouteDecision d = orchestrator.route("我喜欢什么颜色");
        assertTrue(d.needMemory());
        assertTrue(d.memoryTypes().contains("PREFERENCE"));
    }

    @Test
    void parseDecision_skipWhenNeedFalse() {
        MemoryRouteDecision d = orchestrator.parseDecision("{\"needMemory\":false,\"memoryTypes\":[\"PROFILE\"]}");
        assertFalse(d.needMemory());
        assertEquals(Set.of(), d.memoryTypes());
    }

    @Test
    void parseDecision_filtersUnknownTypes() {
        MemoryRouteDecision d = orchestrator.parseDecision(
                "{\"needMemory\":true,\"memoryTypes\":[\"PROFILE\",\"FOO\",\"preference\"]}");
        assertTrue(d.needMemory());
        assertEquals(Set.of("PROFILE", "PREFERENCE"), d.memoryTypes());
    }
}
