package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isA;
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

        ToolCallback echo = tool("echo_ping");
        ToolCallback other = tool("other_tool");

        McpToolRegistry.ToolCallbackProviderDiscovery discovery =
                mock(McpToolRegistry.ToolCallbackProviderDiscovery.class);
        when(discovery.discoverTools(anyBoolean())).thenReturn(List.of(echo, other));

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        McpAllowlistSource source = code -> McpAllowlist.only(Set.of("echo_ping"));

        McpToolRegistry registry = registry(props, discovery, publisher, source, "hash1");

        registry.init();
        assertEquals(1, registry.getTools().size());
        assertEquals("echo_ping", registry.getTools().get(0).getToolDefinition().name());
        verify(publisher, times(1)).publishEvent(isA(McpToolHashChangedEvent.class));
    }

    @Test
    void toolHash_change_triggers_refresh() {
        WujiMcpProperties props = new WujiMcpProperties();
        props.setEnabled(true);
        props.setServerUrl("http://127.0.0.1:8081");

        ToolCallback t1 = tool("t1");
        ToolCallback t2 = tool("t2");

        McpToolRegistry.ToolCallbackProviderDiscovery discovery =
                mock(McpToolRegistry.ToolCallbackProviderDiscovery.class);
        when(discovery.discoverTools(anyBoolean())).thenReturn(List.of(t1), List.of(t2));

        AtomicInteger idx = new AtomicInteger(0);
        List<String> hashes = List.of("hash1", "hash1", "hash2");

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        McpAllowlistSource source = code -> McpAllowlist.allowAll();

        McpToolRegistry registry = new McpToolRegistry(props, discovery, publisher, source, emptyEndpoints()) {
            @Override
            protected AggregatedInfo fetchAggregatedInfo() {
                String h = hashes.get(idx.getAndIncrement());
                return new AggregatedInfo(h, 1, Map.of("wuji-mcp", List.of("t1", "t2")), Map.of());
            }
        };

        registry.init();
        verify(discovery, times(1)).discoverTools(anyBoolean());
        verify(publisher, times(1)).publishEvent(isA(McpToolHashChangedEvent.class));
        assertEquals("t1", registry.getTools().get(0).getToolDefinition().name());

        registry.periodicRefresh();
        verify(discovery, times(1)).discoverTools(anyBoolean());
        verify(publisher, times(1)).publishEvent(isA(McpToolHashChangedEvent.class));

        registry.periodicRefresh();
        verify(discovery, times(2)).discoverTools(anyBoolean());
        verify(publisher, times(2)).publishEvent(isA(McpToolHashChangedEvent.class));
        assertEquals("t2", registry.getTools().get(0).getToolDefinition().name());
    }

    @Test
    void reloadAllowlist_refreshes_even_when_hash_unchanged() {
        WujiMcpProperties props = new WujiMcpProperties();
        props.setEnabled(true);
        props.setServerUrl("http://127.0.0.1:8081");

        ToolCallback echo = tool("echo_ping");
        ToolCallback other = tool("other_tool");

        McpToolRegistry.ToolCallbackProviderDiscovery discovery =
                mock(McpToolRegistry.ToolCallbackProviderDiscovery.class);
        when(discovery.discoverTools(anyBoolean())).thenReturn(List.of(echo, other));

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        AtomicReference<McpAllowlist> policy = new AtomicReference<>(McpAllowlist.allowAll());
        McpAllowlistSource source = code -> policy.get();

        McpToolRegistry registry = registry(props, discovery, publisher, source, "same-hash");

        registry.init();
        assertEquals(2, registry.getTools().size());

        policy.set(McpAllowlist.only(Set.of("echo_ping")));
        registry.reloadAllowlistAndRefresh();

        assertEquals(1, registry.getTools().size());
        assertEquals("echo_ping", registry.getTools().get(0).getToolDefinition().name());
        verify(publisher, times(2)).publishEvent(isA(McpToolHashChangedEvent.class));
        verify(discovery, times(2)).discoverTools(anyBoolean());
    }

    @Test
    void discoverTools_failOnDuplicate_keepsEmptyTools() {
        McpToolRegistry.ToolCallbackProviderDiscovery discovery =
                mock(McpToolRegistry.ToolCallbackProviderDiscovery.class);
        when(discovery.discoverTools(true)).thenThrow(new IllegalStateException("duplicate MCP tool name=echo_ping"));

        WujiMcpProperties props = new WujiMcpProperties();
        props.setServerUrl("http://127.0.0.1:8081");
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        McpToolRegistry registry = registry(props, discovery, publisher, code -> McpAllowlist.allowAll(), "h");
        registry.init();
        assertEquals(0, registry.getTools().size());
    }

    @Test
    void ymlAllowlist_emptyMeansAllowAll() {
        assertEquals(false, YmlMcpAllowlistSource.fromYml(List.of()).restrict());
        assertEquals(true, YmlMcpAllowlistSource.fromYml(List.of("a")).restrict());
        assertEquals(Set.of("a"), YmlMcpAllowlistSource.fromYml(List.of("a")).allowedNames());
    }

    private static McpToolRegistry registry(WujiMcpProperties props,
                                            McpToolRegistry.ToolCallbackProviderDiscovery discovery,
                                            ApplicationEventPublisher publisher,
                                            McpAllowlistSource source,
                                            String hash) {
        return new McpToolRegistry(props, discovery, publisher, source, emptyEndpoints()) {
            @Override
            protected AggregatedInfo fetchAggregatedInfo() {
                return new AggregatedInfo(hash, 1,
                        Map.of("wuji-mcp", List.of("echo_ping", "other_tool", "t1", "t2")),
                        Map.of());
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<McpServerEndpointSource> emptyEndpoints() {
        ObjectProvider<McpServerEndpointSource> op = mock(ObjectProvider.class);
        when(op.getIfAvailable()).thenReturn(null);
        return op;
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
