package com.wuji.assistant.vta.server;

import com.wuji.assistant.agent.config.AgentCoreConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 录音分析助手启动类。
 * <p>
 * 扫描范围刻意收窄：只装 VTA + LLM 调用链（ModelRouter / Prompt / 审计），
 * 不扫 ChatFacade / AgentFactory / Memory / RAG，避免无记忆库约束被聊天侧 Bean 拖垮。
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.wuji.assistant.vta",
                "com.wuji.assistant.agent.model",
                "com.wuji.assistant.agent.prompt",
                "com.wuji.assistant.agent.observability"
        },
        exclude = ReactiveUserDetailsServiceAutoConfiguration.class
)
@Import(AgentCoreConfiguration.class)
public class VoiceTextAssistantAgentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceTextAssistantAgentServerApplication.class, args);
    }
}
