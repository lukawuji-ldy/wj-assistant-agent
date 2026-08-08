package com.wuji.assistant.mcp.info;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * MCP server 信息响应（用于客户端发现与版本/目录变更判断）。
 *
 * @param name          Server 名
 * @param serverVersion 版本
 * @param protocol      协议标注
 * @param build         构建号（可空）
 * @param capabilities  能力
 * @param toolHash      工具目录哈希
 * @param tools         工具摘要（管理台目录合并用；不影响 toolHash 算法）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpInfoResponse(
        String name,
        String serverVersion,
        String protocol,
        String build,
        List<String> capabilities,
        String toolHash,
        List<ToolSummary> tools
) {
    /**
     * 工具摘要。
     *
     * @param name         工具名
     * @param description  描述
     * @param inputSchema  参数 JSON Schema（P5.2；参与 toolHash 计算的规范化原文）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolSummary(String name, String description, JsonNode inputSchema) {
    }
}
