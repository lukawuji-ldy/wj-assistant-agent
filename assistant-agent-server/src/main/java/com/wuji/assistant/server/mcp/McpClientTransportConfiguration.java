package com.wuji.assistant.server.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiMcpProperties;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * 自建 MCP SSE Transport，支持可选 Bearer API Key（不污染全局 WebClient）。
 *
 * @author liudy
 */
@Configuration
@ConditionalOnProperty(prefix = "wuji.mcp", name = "enabled", havingValue = "true")
public class McpClientTransportConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpClientTransportConfiguration.class);

    /**
     * 覆盖 yaml sse.connections：单一来源为 {@code wuji.mcp.server-url} + auth。
     *
     * @param mcpProperties MCP 配置
     * @param objectMapper  JSON
     * @return 命名传输列表
     */
    @Bean
    public List<NamedClientMcpTransport> wujiMcpSseTransports(WujiMcpProperties mcpProperties,
                                                              ObjectMapper objectMapper) {
        WebClient.Builder builder = WebClient.builder().baseUrl(mcpProperties.getServerUrl());
        if (mcpProperties.getAuth().isEnabled()) {
            String apiKey = mcpProperties.getAuth().getApiKey();
            if (!StringUtils.hasText(apiKey)) {
                log.warn("wuji.mcp.auth.enabled=true but api-key empty; MCP calls will likely get 401");
            } else {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
        }
        String endpoint = StringUtils.hasText(mcpProperties.getSseEndpoint())
                ? mcpProperties.getSseEndpoint()
                : "/sse";
        var transport = WebFluxSseClientTransport.builder(builder)
                .sseEndpoint(endpoint)
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .build();
        log.info("MCP SSE transport ready url={} authEnabled={}",
                mcpProperties.getServerUrl(), mcpProperties.getAuth().isEnabled());
        return List.of(new NamedClientMcpTransport("wuji-mcp", transport));
    }
}
