package com.wuji.assistant.rag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 模块自动配置。
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties(RagVectorProperties.class)
public class RagAutoConfiguration {
}
