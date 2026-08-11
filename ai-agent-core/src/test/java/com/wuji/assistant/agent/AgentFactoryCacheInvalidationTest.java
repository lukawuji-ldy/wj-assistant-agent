package com.wuji.assistant.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.wuji.assistant.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.tool.BuiltinToolProvider;
import com.wuji.assistant.agent.prompt.PromptTemplateChangedEvent;
import com.wuji.assistant.agent.tool.McpToolHashChangedEvent;
import com.wuji.assistant.agent.tool.McpToolProvider;
import com.wuji.assistant.agent.tool.RagToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFactoryCacheInvalidationTest {

    @Test
    void toolHashChanged_shouldClearAgentCache() {
        LlmClientFactory llmClientFactory = mock(LlmClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(llmClientFactory.getChatModel("configId")).thenReturn(chatModel);

        CheckpointSaverFactory checkpointSaverFactory = mock(CheckpointSaverFactory.class);
        BaseCheckpointSaver saver = mock(BaseCheckpointSaver.class);
        when(checkpointSaverFactory.getSaver()).thenReturn(saver);

        WujiAgentProperties agentProperties = new WujiAgentProperties();
        BuiltinToolProvider builtinToolProvider = mock(BuiltinToolProvider.class);
        RagToolProvider ragToolProvider = mock(RagToolProvider.class);
        McpToolProvider mcpToolProvider = mock(McpToolProvider.class);
        when(builtinToolProvider.getTools()).thenReturn(List.<ToolCallback>of());
        when(ragToolProvider.getTools()).thenReturn(List.<ToolCallback>of());
        when(mcpToolProvider.getTools()).thenReturn(List.<ToolCallback>of());

        AgentFactory factory = new AgentFactory(
                llmClientFactory,
                agentProperties,
                checkpointSaverFactory,
                builtinToolProvider,
                ragToolProvider,
                mcpToolProvider
        );

        ReactAgent a1 = factory.getOrCreate("configId");
        ReactAgent a2 = factory.getOrCreate("configId");
        assertSame(a1, a2);

        factory.onMcpToolHashChanged(new McpToolHashChangedEvent("hash2"));

        ReactAgent a3 = factory.getOrCreate("configId");
        assertNotSame(a1, a3);
    }

    @Test
    void promptChanged_shouldClearAgentCache() {
        LlmClientFactory llmClientFactory = mock(LlmClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(llmClientFactory.getChatModel("configId")).thenReturn(chatModel);

        CheckpointSaverFactory checkpointSaverFactory = mock(CheckpointSaverFactory.class);
        BaseCheckpointSaver saver = mock(BaseCheckpointSaver.class);
        when(checkpointSaverFactory.getSaver()).thenReturn(saver);

        WujiAgentProperties agentProperties = new WujiAgentProperties();
        BuiltinToolProvider builtinToolProvider = mock(BuiltinToolProvider.class);
        RagToolProvider ragToolProvider = mock(RagToolProvider.class);
        McpToolProvider mcpToolProvider = mock(McpToolProvider.class);
        when(builtinToolProvider.getTools()).thenReturn(List.<ToolCallback>of());
        when(ragToolProvider.getTools()).thenReturn(List.<ToolCallback>of());
        when(mcpToolProvider.getTools()).thenReturn(List.<ToolCallback>of());

        AgentFactory factory = new AgentFactory(
                llmClientFactory,
                agentProperties,
                checkpointSaverFactory,
                builtinToolProvider,
                ragToolProvider,
                mcpToolProvider
        );

        ReactAgent a1 = factory.getOrCreate("configId");
        factory.onPromptTemplateChanged(new PromptTemplateChangedEvent("rag.knowledge_retrieval.system"));
        ReactAgent a2 = factory.getOrCreate("configId");
        assertNotSame(a1, a2);
    }
}

