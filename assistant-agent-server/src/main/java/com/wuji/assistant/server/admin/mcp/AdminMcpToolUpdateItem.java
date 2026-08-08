package com.wuji.assistant.server.admin.mcp;

/**
 * 批量更新单个工具的绑定 / 启用态（P5.1）。
 *
 * @author liudy
 */
public record AdminMcpToolUpdateItem(String toolName, boolean bound, boolean enabled) {
}
