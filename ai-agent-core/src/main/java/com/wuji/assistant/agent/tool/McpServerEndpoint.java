package com.wuji.assistant.agent.tool;

/**
 * MCP Server 运行时端点（供 /mcp/info 与 allowlist）。
 *
 * @param serverCode   业务键
 * @param baseUrl      base URL
 * @param bearerToken  Bearer 明文，可空
 * @author liudy
 */
public record McpServerEndpoint(String serverCode, String baseUrl, String bearerToken) {
}
