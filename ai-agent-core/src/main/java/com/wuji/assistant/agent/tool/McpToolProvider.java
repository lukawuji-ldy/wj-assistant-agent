package com.wuji.assistant.agent.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * MCP 工具提供者 SPI。启用时由 {@link ClientMcpToolProvider} 实现；关闭时为 {@link EmptyMcpToolProvider}。
 *
 * @author liudy
 */
public interface McpToolProvider {

    /**
     * @return 工具回调列表
     */
    List<ToolCallback> getTools();
}
