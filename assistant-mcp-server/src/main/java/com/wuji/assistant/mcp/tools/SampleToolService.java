package com.wuji.assistant.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP 样例工具：连通性与天气 mock。
 *
 * @author liudy
 */
@Service
public class SampleToolService {

    /**
     * 连通性检查。
     *
     * @param message 回显内容
     * @return pong 文本
     */
    @Tool(name = "echo_ping", description = "Connectivity check; echoes the input message with a pong prefix")
    public String echoPing(@ToolParam(description = "Message to echo") String message) {
        return "pong: " + (message == null ? "" : message);
    }
}

