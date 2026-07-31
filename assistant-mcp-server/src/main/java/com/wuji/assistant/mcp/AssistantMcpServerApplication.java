package com.wuji.assistant.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP 服务启动类（首期空壳，后续注册 Tool）。
 *
 * @author liudy
 */
@SpringBootApplication(scanBasePackages = "com.wuji.assistant.mcp")
public class AssistantMcpServerApplication {

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AssistantMcpServerApplication.class, args);
    }
}
