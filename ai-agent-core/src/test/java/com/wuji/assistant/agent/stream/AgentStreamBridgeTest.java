package com.wuji.assistant.agent.stream;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AgentStreamBridge 单元测试。
 *
 * @author liudy
 */
class AgentStreamBridgeTest {

    @Test
    void toContentDeltas_keepsAssistantTextOnly() {
        List<String> deltas = AgentStreamBridge.toContentDeltas(Flux.just(
                new SystemMessage("sys"),
                new UserMessage("hi"),
                new AssistantMessage("hello"),
                new AssistantMessage(" world")
        )).collectList().block();

        assertEquals(List.of("hello", " world"), deltas);
    }
}
