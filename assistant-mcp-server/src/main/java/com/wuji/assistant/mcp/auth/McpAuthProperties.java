package com.wuji.assistant.mcp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP Server API Key 鉴权配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.mcp.auth")
public class McpAuthProperties {

    /**
     * 是否启用鉴权；本地可 false，生产建议 true。
     */
    private boolean enabled = false;

    /**
     * 共享密钥，优先从环境变量 {@code WUJI_MCP_API_KEY} 注入。
     */
    private String apiKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
