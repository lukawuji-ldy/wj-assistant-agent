package com.wuji.assistant.server.admin.mcp;

/**
 * 创建 MCP Server 请求。
 *
 * @author liudy
 */
public record AdminMcpServerCreateRequest(
        String serverCode,
        String displayName,
        String baseUrl,
        String sseEndpoint,
        String authType,
        String authToken,
        Integer sortOrder
) {
}
