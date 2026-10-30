package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具说明来自 prompt_template，并交给领域检索工具。
 *
 * @author liudy
 */
class DefaultRagToolProviderTest {

    @Test
    void getToolsUsesPromptDescription() {
        PromptTemplateService prompts = mock(PromptTemplateService.class);
        DomainKnowledgeTool domainTool = mock(DomainKnowledgeTool.class);
        ToolCallback callback = mock(ToolCallback.class);
        when(prompts.loadAndRender(eq("rag.knowledge_retrieval.system"), eq(Map.of()), anyString()))
                .thenReturn("from-db");
        when(domainTool.callback("from-db")).thenReturn(callback);

        DefaultRagToolProvider provider = new DefaultRagToolProvider(new WujiRagProperties(), prompts, domainTool);

        List<ToolCallback> tools = provider.getTools();
        assertEquals(1, tools.size());
        assertSame(callback, tools.get(0));
        verify(domainTool).callback("from-db");
    }
}
