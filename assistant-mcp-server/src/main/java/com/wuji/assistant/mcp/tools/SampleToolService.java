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

    /**
     * 按城市返回 mock 天气。
     *
     * @param city 城市名
     * @return 天气描述
     */
    @Tool(name = "get_weather", description = "Get mock weather for a city (sample tool, not live data)")
    public String getWeather(@ToolParam(description = "City name") String city) {
        String name = city == null || city.isBlank() ? "unknown" : city.trim();
        return "Weather in " + name + ": sunny, 25C (mock)";
    }
}
