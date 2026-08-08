package com.wuji.assistant.server.admin.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP 工具详情（P5.2）。
 *
 * @author liudy
 */
public record AdminMcpToolDetailView(
        String toolName,
        String description,
        JsonNode inputSchema,
        boolean bound,
        boolean enabled,
        String serverCode,
        String serverVersion,
        String toolHash,
        String source
) {
}
