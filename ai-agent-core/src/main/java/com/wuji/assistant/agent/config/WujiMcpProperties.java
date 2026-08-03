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

    private String sseEndpoint = "/sse";

    private Auth auth = new Auth();

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

    public String getSseEndpoint() {
        return sseEndpoint;
    }

    public void setSseEndpoint(String sseEndpoint) {
        this.sseEndpoint = sseEndpoint;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    /**
     * Client 侧与 MCP Server 共享的 API Key 鉴权。
     */
    public static class Auth {

        private boolean enabled = false;

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
}
