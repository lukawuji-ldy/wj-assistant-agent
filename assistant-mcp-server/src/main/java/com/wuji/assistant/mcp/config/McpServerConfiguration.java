package com.wuji.assistant.mcp.config;

import com.wuji.assistant.mcp.tools.SampleToolService;
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
     * 注册样例工具。
     *
     * @param sampleToolService 工具服务
     * @return ToolCallbackProvider
     */
    @Bean
    public ToolCallbackProvider sampleTools(SampleToolService sampleToolService) {
        return MethodToolCallbackProvider.builder().toolObjects(sampleToolService).build();
    }
}
