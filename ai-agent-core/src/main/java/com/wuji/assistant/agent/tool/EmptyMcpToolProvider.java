package com.wuji.assistant.agent.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 关闭时的空实现。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "wuji.mcp", name = "enabled", havingValue = "false")
public class EmptyMcpToolProvider implements McpToolProvider {

    @Override
    public List<ToolCallback> getTools() {
        return List.of();
    }
}
