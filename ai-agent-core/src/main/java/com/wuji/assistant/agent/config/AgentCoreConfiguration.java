package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 核心配置装配。
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties({
        WujiModelProperties.class,
        WujiAgentProperties.class,
        WujiMcpProperties.class,
        WujiRagProperties.class,
        WujiMemoryProperties.class
})
public class AgentCoreConfiguration {
}
