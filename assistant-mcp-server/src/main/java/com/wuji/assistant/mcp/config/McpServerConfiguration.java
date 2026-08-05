package com.wuji.assistant.mcp.config;

import com.wuji.assistant.mcp.tools.OpenMeteoService;
import com.wuji.assistant.mcp.tools.SampleToolService;
import com.wuji.assistant.mcp.tools.TimeService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server Tool 装配。
 *
 * @author liudy
 */
@Configuration
public class McpServerConfiguration {

    /**
     * 注册样例工具（echo_ping / get_weather）。
     *
     * @param sampleToolService 工具服务
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider sampleTools(SampleToolService sampleToolService) {
        return MethodToolCallbackProvider.builder().toolObjects(sampleToolService).build();
    }

    /**
     * 注册 OpenMeteo 演示工具。
     *
     * @param openMeteoService 工具服务
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider openMeteoTools(OpenMeteoService openMeteoService) {
        return MethodToolCallbackProvider.builder().toolObjects(openMeteoService).build();
    }

    /**
     * 注册时间/出行演示工具。
     *
     * @param timeService 工具服务
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider timeTools(TimeService timeService) {
        return MethodToolCallbackProvider.builder().toolObjects(timeService).build();
    }
}
