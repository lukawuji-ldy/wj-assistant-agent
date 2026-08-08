package com.wuji.assistant.server.admin.mcp;

/**
 * 更新 MCP Server 请求；authToken 空则不改密文。
 *
 * @author liudy
 */
public record AdminMcpServerUpdateRequest(
        String displayName,
        String baseUrl,
        String sseEndpoint,
        String authType,
        String authToken,
        String status,
        Integer sortOrder
) {
}
