package com.wuji.assistant.server.mcp;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import com.wuji.assistant.agent.model.ApiKeyCipherService;
import com.wuji.assistant.agent.tool.McpServerEndpoint;
import com.wuji.assistant.agent.tool.McpServerEndpointSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@code mcp_server_ref} 提供 ACTIVE 端点；空库回落 yml。
 *
 * @author liudy
 */
@Component
public class DbMcpServerEndpointSource implements McpServerEndpointSource {

    private final McpServerConnectionRepository repository;
    private final ApiKeyCipherService apiKeyCipherService;
    private final WujiMcpProperties mcpProperties;

    public DbMcpServerEndpointSource(McpServerConnectionRepository repository,
                                     ApiKeyCipherService apiKeyCipherService,
                                     WujiMcpProperties mcpProperties) {
        this.repository = repository;
        this.apiKeyCipherService = apiKeyCipherService;
        this.mcpProperties = mcpProperties;
    }

    @Override
    public List<McpServerEndpoint> listActiveEndpoints() {
        List<McpServerConnection> active = repository.listActive();
        if (active.isEmpty()) {
            String token = null;
            if (mcpProperties.getAuth() != null && mcpProperties.getAuth().isEnabled()
                    && StringUtils.hasText(mcpProperties.getAuth().getApiKey())) {
                token = mcpProperties.getAuth().getApiKey();
            }
            return List.of(new McpServerEndpoint(
                    "wuji-mcp",
                    mcpProperties.getServerUrl(),
                    token));
        }
        List<McpServerEndpoint> out = new ArrayList<>();
        for (McpServerConnection conn : active) {
            String token = null;
            if (conn.bearer() && StringUtils.hasText(conn.authTokenCipher())) {
                token = apiKeyCipherService.decrypt(conn.authTokenCipher());
            }
            out.add(new McpServerEndpoint(conn.serverCode(), conn.baseUrl(), token));
        }
        return List.copyOf(out);
    }
}
