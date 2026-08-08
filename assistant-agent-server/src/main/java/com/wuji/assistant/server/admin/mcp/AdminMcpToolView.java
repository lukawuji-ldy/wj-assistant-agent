package com.wuji.assistant.server.admin.mcp;

/**
 * MCP 工具绑定视图（远端 ∪ 库）。
 *
 * @author liudy
 */
public record AdminMcpToolView(
        String toolName,
        String description,
        boolean enabled,
        boolean bound
) {
}
