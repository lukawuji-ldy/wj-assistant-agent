package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP Client 配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.mcp")
public class WujiMcpProperties {

    private boolean enabled = true;

    private String serverUrl = "http://127.0.0.1:8081";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }
}
