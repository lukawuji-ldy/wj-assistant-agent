package com.wuji.assistant.server.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiMcpProperties;
import com.wuji.assistant.agent.model.ApiKeyCipherService;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 按连接行构建 SSE {@link NamedClientMcpTransport}。
 *
 * @author liudy
 */
@Component
public class McpSseTransportFactory {

    private static final Logger log = LoggerFactory.getLogger(McpSseTransportFactory.class);

    private final WujiMcpProperties mcpProperties;
    private final ApiKeyCipherService apiKeyCipherService;
    private final ObjectMapper objectMapper;

    public McpSseTransportFactory(WujiMcpProperties mcpProperties,
                                  ApiKeyCipherService apiKeyCipherService,
                                  ObjectMapper objectMapper) {
        this.mcpProperties = mcpProperties;
        this.apiKeyCipherService = apiKeyCipherService;
        this.objectMapper = objectMapper;
    }

    public NamedClientMcpTransport build(McpServerConnection conn) {
        String baseUrl = conn.baseUrl();
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        if (conn.bearer()) {
            String plain = apiKeyCipherService.decrypt(conn.authTokenCipher());
            if (!StringUtils.hasText(plain)) {
                log.warn("MCP server {} auth_type=BEARER but token empty; calls may get 401", conn.serverCode());
            } else {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + plain);
            }
        }
        String endpoint = StringUtils.hasText(conn.sseEndpoint())
                ? conn.sseEndpoint().trim()
                : (StringUtils.hasText(mcpProperties.getSseEndpoint())
                ? mcpProperties.getSseEndpoint() : "/sse");
        var transport = new WujiWebFluxSseClientTransport(
                builder, new JacksonMcpJsonMapper(objectMapper), endpoint);
        log.info("MCP SSE transport ready serverCode={} url={} authType={} transport={}",
                conn.serverCode(), baseUrl, conn.authType(), transport.getClass().getSimpleName());
        return new NamedClientMcpTransport(conn.serverCode(), transport);
    }

    /**
     * yml 空库兜底单 Transport。
     */
    public NamedClientMcpTransport buildFromYmlFallback() {
        McpServerConnection synthetic = new McpServerConnection(
                "wuji-mcp",
                "yml-fallback",
                mcpProperties.getServerUrl(),
                mcpProperties.getSseEndpoint(),
                mcpProperties.getAuth() != null && mcpProperties.getAuth().isEnabled() ? "BEARER" : "NONE",
                null,
                "ACTIVE",
                0);
        WebClient.Builder builder = WebClient.builder().baseUrl(mcpProperties.getServerUrl());
        if (mcpProperties.getAuth() != null && mcpProperties.getAuth().isEnabled()) {
            String apiKey = mcpProperties.getAuth().getApiKey();
            if (StringUtils.hasText(apiKey)) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            } else {
                log.warn("wuji.mcp.auth.enabled=true but api-key empty; MCP calls will likely get 401");
            }
        }
        String endpoint = StringUtils.hasText(mcpProperties.getSseEndpoint())
                ? mcpProperties.getSseEndpoint() : "/sse";
        var transport = new WujiWebFluxSseClientTransport(
                builder, new JacksonMcpJsonMapper(objectMapper), endpoint);
        log.info("MCP SSE transport ready (yml fallback) url={} authEnabled={}",
                mcpProperties.getServerUrl(),
                mcpProperties.getAuth() != null && mcpProperties.getAuth().isEnabled());
        return new NamedClientMcpTransport(synthetic.serverCode(), transport);
    }
}
