package com.wuji.assistant.agent.tool;

/**
 * MCP toolHash 变化事件：用于触发 Agent 侧缓存失效（如 ReactAgent 重建）。
 */
public record McpToolHashChangedEvent(String toolHash) {
}

