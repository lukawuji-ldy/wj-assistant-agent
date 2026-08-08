package com.wuji.assistant.server.admin.mcp;

import java.util.List;

/**
 * 批量更新请求体。
 *
 * @author liudy
 */
public record AdminMcpToolsUpdateRequest(List<AdminMcpToolUpdateItem> tools) {
}
