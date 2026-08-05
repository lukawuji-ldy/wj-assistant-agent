package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Agent allowlist：只保留白名单内的 MCP tools 注入到 ReactAgent。
     * 若为空或未配置：保留发现到的全部 tools。
     */
    private List<String> includeTools = new ArrayList<>();

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

    public List<String> getIncludeTools() {
        return includeTools;
    }

    public void setIncludeTools(List<String> includeTools) {
        this.includeTools = includeTools == null ? new ArrayList<>() : includeTools;
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
