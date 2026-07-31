package com.wuji.assistant.agent.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * MCP 工具提供者（本期空壳，下期接 MCP Client 发现）。
 *
 * @author liudy
 */
public interface McpToolProvider {

    /**
     * @return 工具回调列表
     */
    List<ToolCallback> getTools();
}
