package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.rag.KnowledgeRetrievalToolFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具说明来自 prompt_template。
 *
 * @author liudy
 */
class DefaultRagToolProviderTest {

    @Test
    void getToolsUsesPromptDescription() {
        KnowledgeRetrievalToolFactory factory = mock(KnowledgeRetrievalToolFactory.class);
        PromptTemplateService prompts = mock(PromptTemplateService.class);
        ToolCallback callback = mock(ToolCallback.class);
        when(prompts.loadAndRender(eq("rag.knowledge_retrieval.system"), eq(Map.of()), anyString()))
                .thenReturn("from-db");
        when(factory.create(anyInt(), anyDouble(), eq("from-db"))).thenReturn(callback);

        DefaultRagToolProvider provider = new DefaultRagToolProvider(
                factory, new WujiRagProperties(), prompts);

        List<ToolCallback> tools = provider.getTools();
        assertEquals(1, tools.size());
        assertSame(callback, tools.get(0));
        verify(factory).create(5, 0.72, "from-db");
    }
}
