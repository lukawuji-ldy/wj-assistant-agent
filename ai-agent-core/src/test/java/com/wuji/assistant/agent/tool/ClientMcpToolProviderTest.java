package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ClientMcpToolProvider 单测。
 *
 * @author liudy
 */
class ClientMcpToolProviderTest {

    @Test
    void isMcpProvider_matchesMcpToolCallbackName() {
        assertTrue(ClientMcpToolProvider.isMcpProvider(new StubMcpToolCallbackProvider()));
    }

    @Test
    void isMcpProvider_rejectsLocalMethodProvider() {
        ToolCallbackProvider local = mock(ToolCallbackProvider.class);
        assertFalse(ClientMcpToolProvider.isMcpProvider(local));
    }

    @Test
    void getTools_delegatesToRegistry() {
        ToolCallback cb = tool("echo_ping");
        McpToolRegistry registry = mock(McpToolRegistry.class);
        when(registry.getTools()).thenReturn(List.of(cb));

        ClientMcpToolProvider provider = new ClientMcpToolProvider(registry, props());
        assertEquals(1, provider.getTools().size());
        assertEquals("echo_ping", provider.getTools().get(0).getToolDefinition().name());
    }

    /**
     * 类名含 McpToolCallback，供 {@link ClientMcpToolProvider#isMcpProvider} 识别。
     */
    static final class StubMcpToolCallbackProvider implements ToolCallbackProvider {

        private final ToolCallback[] callbacks;

        StubMcpToolCallbackProvider(ToolCallback... callbacks) {
            this.callbacks = callbacks == null ? new ToolCallback[0] : callbacks;
        }

        @Override
        public ToolCallback[] getToolCallbacks() {
            return callbacks;
        }
    }

    static final class FailingMcpToolCallbackProvider implements ToolCallbackProvider {
        @Override
        public ToolCallback[] getToolCallbacks() {
            throw new IllegalStateException("Multiple tools with the same name (echo_ping)");
        }
    }

    private static WujiMcpProperties props() {
        WujiMcpProperties p = new WujiMcpProperties();
        p.setEnabled(true);
        p.setServerUrl("http://127.0.0.1:8081");
        return p;
    }

    private static ToolCallback tool(String name) {
        ToolCallback cb = mock(ToolCallback.class);
        when(cb.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name(name)
                .description(name)
                .inputSchema("{}")
                .build());
        return cb;
    }
}
