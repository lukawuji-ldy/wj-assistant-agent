package com.wuji.assistant.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent 主服务启动类。
 *
 * @author liudy
 */
@SpringBootApplication(
        scanBasePackages = "com.wuji.assistant",
        exclude = ReactiveUserDetailsServiceAutoConfiguration.class
)
@EnableScheduling
public class AssistantAgentServerApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AssistantAgentServerApplication.class, args);
    }
}
