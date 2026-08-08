package com.wuji.assistant.server.mcp;

/**
 * MCP Server 连接行（库表权威）。
 *
 * @param serverCode       业务键
 * @param displayName      展示名
 * @param baseUrl          base URL
 * @param sseEndpoint      SSE 端点，可空
 * @param authType         NONE|BEARER
 * @param authTokenCipher  Bearer 密文，可空
 * @param status           ACTIVE|DISABLED
 * @param sortOrder        排序
 * @author liudy
 */
public record McpServerConnection(
        String serverCode,
        String displayName,
        String baseUrl,
        String sseEndpoint,
        String authType,
        String authTokenCipher,
        String status,
        int sortOrder
) {
    public boolean active() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean bearer() {
        return "BEARER".equalsIgnoreCase(authType);
    }
}
