package com.wuji.assistant.agent.stream;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void toContentDeltas_swallowed429_errorsInsteadOfDelta() {
        Flux<String> deltas = AgentStreamBridge.toContentDeltas(Flux.just(
                new AssistantMessage("Exception: 429 - {\"code\":429,\"reason\":\"RateLimitExceeded\"}")
        ));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> deltas.collectList().block());
        assertInstanceOf(WujiException.class, thrown);
        assertEquals(ErrorCode.MODEL_RATE_LIMITED, ((WujiException) thrown).getErrorCode());
    }
}
