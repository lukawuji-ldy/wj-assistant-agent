package com.wuji.assistant.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitExceededException;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitExceededException;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.wuji.assistant.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.tool.BuiltinToolProvider;
import com.wuji.assistant.agent.tool.McpToolProvider;
import com.wuji.assistant.agent.tool.RagToolProvider;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentFactory 单元测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentFactoryTest {

    @Mock
    private LlmClientFactory llmClientFactory;

    @Mock
    private CheckpointSaverFactory checkpointSaverFactory;

    @Mock
    private BuiltinToolProvider builtinToolProvider;

    @Mock
    private RagToolProvider ragToolProvider;

    @Mock
    private McpToolProvider mcpToolProvider;

    private AgentFactory agentFactory;

    @BeforeEach
    void setUp() {
        WujiAgentProperties props = new WujiAgentProperties();
        props.setId("default");
        props.setMaxModelCalls(16);
        props.setMaxToolRounds(8);
        props.getCheckpoint().setType("memory");
        when(builtinToolProvider.getTools()).thenReturn(List.of());
        when(ragToolProvider.getTools()).thenReturn(List.of());
        when(mcpToolProvider.getTools()).thenReturn(List.of());
        when(checkpointSaverFactory.getSaver()).thenReturn(new MemorySaver());
        agentFactory = new AgentFactory(llmClientFactory, props, checkpointSaverFactory,
                builtinToolProvider, ragToolProvider, mcpToolProvider);
    }

    @Test
    void getOrCreate_cachesSameConfigId() {
        ChatModel chatModel = mock(ChatModel.class);
        when(llmClientFactory.getChatModel("llm_primary")).thenReturn(chatModel);

        ReactAgent first = agentFactory.getOrCreate("llm_primary");
        ReactAgent second = agentFactory.getOrCreate("llm_primary");

        assertSame(first, second);
        verify(llmClientFactory, times(1)).getChatModel("llm_primary");
    }

    @Test
    void mapLimitException_modelCallLimit() {
        RuntimeException mapped = AgentFactory.mapLimitException(
                new ModelCallLimitExceededException(0, 16, null, 16));
        assertInstanceOf(WujiException.class, mapped);
        assertEquals(ErrorCode.AGENT_MAX_ITERATIONS, ((WujiException) mapped).getErrorCode());
    }

    @Test
    void mapLimitException_toolCallLimit_wrapped() {
        RuntimeException mapped = AgentFactory.mapLimitException(
                new RuntimeException(new ToolCallLimitExceededException(0, 8, null, 8, null)));
        assertInstanceOf(WujiException.class, mapped);
        assertEquals(ErrorCode.AGENT_MAX_ITERATIONS, ((WujiException) mapped).getErrorCode());
    }
}
