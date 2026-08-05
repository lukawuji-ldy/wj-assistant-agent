package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolRegistryTest {

    @Test
    void allowlist_filters_tools() {
        WujiMcpProperties props = new WujiMcpProperties();
        props.setEnabled(true);
        props.setServerUrl("http://127.0.0.1:8081");
        props.setIncludeTools(List.of("echo_ping"));

        McpToolRegistry.ToolCallbackProviderDiscovery discovery =
                mock(McpToolRegistry.ToolCallbackProviderDiscovery.class);
        when(discovery.discoverTools()).thenReturn(List.of(
                tool("echo_ping"),
                tool("other_tool")
        ));

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        McpToolRegistry registry = new McpToolRegistry(props, discovery, publisher) {
            @Override
            protected McpInfo fetchMcpInfo() {
                return new McpToolRegistry.McpInfo("hash1");
            }
        };

        registry.init();
        assertEquals(1, registry.getTools().size());
        assertEquals("echo_ping", registry.getTools().get(0).getToolDefinition().name());
        verify(publisher, times(1)).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void toolHash_change_triggers_refresh() {
        WujiMcpProperties props = new WujiMcpProperties();
        props.setEnabled(true);
        props.setServerUrl("http://127.0.0.1:8081");
        props.setIncludeTools(List.of()); // allowlist disabled

        McpToolRegistry.ToolCallbackProviderDiscovery discovery =
                mock(McpToolRegistry.ToolCallbackProviderDiscovery.class);
        when(discovery.discoverTools()).thenReturn(
                List.of(tool("t1")),
                List.of(tool("t2"))
        );

        AtomicInteger idx = new AtomicInteger(0);
        List<String> hashes = List.of("hash1", "hash1", "hash2");

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        McpToolRegistry registry = new McpToolRegistry(props, discovery, publisher) {
            @Override
            protected McpInfo fetchMcpInfo() {
                return new McpToolRegistry.McpInfo(hashes.get(idx.getAndIncrement()));
            }
        };

        registry.init(); // hash1 -> discover
        verify(discovery, times(1)).discoverTools();
        verify(publisher, times(1)).publishEvent(org.mockito.ArgumentMatchers.any());
        assertEquals("t1", registry.getTools().get(0).getToolDefinition().name());

        registry.periodicRefresh(); // hash1 -> no discover
        verify(discovery, times(1)).discoverTools();
        verify(publisher, times(1)).publishEvent(org.mockito.ArgumentMatchers.any());
        assertEquals("t1", registry.getTools().get(0).getToolDefinition().name());

        registry.periodicRefresh(); // hash2 -> discover
        verify(discovery, times(2)).discoverTools();
        verify(publisher, times(2)).publishEvent(org.mockito.ArgumentMatchers.any());
        assertEquals("t2", registry.getTools().get(0).getToolDefinition().name());
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

