package com.wuji.assistant.mcp.config;

import com.wuji.assistant.mcp.auth.McpAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 MCP 鉴权配置属性。
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties(McpAuthProperties.class)
public class McpAuthConfiguration {
}
