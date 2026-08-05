package com.wuji.assistant.mcp.info;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * MCP server 信息响应（用于客户端发现与版本/目录变更判断）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpInfoResponse(
        String name,
        String serverVersion,
        String protocol,
        String build,
        List<String> capabilities,
        String toolHash
) {
}

