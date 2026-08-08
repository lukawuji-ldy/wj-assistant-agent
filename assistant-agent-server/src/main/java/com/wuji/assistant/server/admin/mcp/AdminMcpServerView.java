package com.wuji.assistant.server.admin.mcp;

/**
 * MCP Server 管理视图（P5.2）。
 *
 * @author liudy
 */
public record AdminMcpServerView(
        String serverCode,
        String displayName,
        String status,
        String baseUrl,
        String sseEndpoint,
        String authType,
        String authTokenMasked,
        String authTokenPreview,
        Integer sortOrder
) {
}
