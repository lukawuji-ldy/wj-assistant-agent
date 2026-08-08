package com.wuji.assistant.server.admin.mcp;

import java.util.List;

/**
 * 工具列表响应。
 *
 * @param tools  工具行
 * @param source REMOTE_MERGED | DB_ONLY
 * @author liudy
 */
public record AdminMcpToolsResponse(List<AdminMcpToolView> tools, String source) {
}
