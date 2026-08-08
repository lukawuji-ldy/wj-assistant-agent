package com.wuji.assistant.agent.tool;

/**
 * MCP 工具允许名单来源（yml 或 DB 优先）。
 *
 * @author liudy
 */
public interface McpAllowlistSource {

    /** 默认单端点业务键（与 DevSeed / Admin API 一致）。 */
    String DEFAULT_SERVER_CODE = "wuji-mcp";

    /**
     * 解析当前应生效的 allowlist。
     *
     * @param serverCode MCP server 业务键
     * @return 策略
     */
    McpAllowlist resolve(String serverCode);
}
