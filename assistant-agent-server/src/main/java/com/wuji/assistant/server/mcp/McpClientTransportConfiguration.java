package com.wuji.assistant.server.mcp;

import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 自建 MCP SSE Transport：优先 ACTIVE {@code mcp_server_ref}，空库回落 yml。
 *
 * @author liudy
 */
@Configuration
@ConditionalOnProperty(prefix = "wuji.mcp", name = "enabled", havingValue = "true")
public class McpClientTransportConfiguration {

    @Bean
    public List<NamedClientMcpTransport> wujiMcpSseTransports(McpServerConnectionRepository repository,
                                                              McpSseTransportFactory transportFactory) {
        List<McpServerConnection> active = repository.listActive();
        if (active.isEmpty()) {
            return List.of(transportFactory.buildFromYmlFallback());
        }
        List<NamedClientMcpTransport> transports = new ArrayList<>();
        for (McpServerConnection conn : active) {
            transports.add(transportFactory.build(conn));
        }
        return List.copyOf(transports);
    }

    /**
     * Spring AI 默认把整个 ToolContext 写入 MCP {@code _meta}。这里换成只保留 JSON 值的转换器。
     */
    @Bean
    @ConditionalOnMissingBean(ToolContextToMcpMetaConverter.class)
    public ToolContextToMcpMetaConverter toolContextToMcpMetaConverter() {
        return new JsonSafeMcpMetaConverter();
    }
}
