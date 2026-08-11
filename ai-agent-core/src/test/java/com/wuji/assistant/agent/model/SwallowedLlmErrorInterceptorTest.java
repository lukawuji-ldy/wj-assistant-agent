package com.wuji.assistant.agent.model;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 拦截 AgentLlmNode 吞掉的 LLM 异常，重新抛出供 ModelRouter 主备切换。
 *
 * @author liudy
 */
class SwallowedLlmErrorInterceptorTest {

    @Test
    void swallowed429_isRethrownAsRateLimited() {
        SwallowedLlmErrorInterceptor interceptor = new SwallowedLlmErrorInterceptor();
        ModelCallHandler handler = req -> ModelResponse.of(new AssistantMessage(
                "Exception: 429 - {\"code\":429,\"reason\":\"RateLimitExceeded\"}"));
        ModelRequest request = ModelRequest.builder()
                .messages(List.of(new UserMessage("合同基本信息")))
                .build();

        WujiException thrown = assertThrows(WujiException.class,
                () -> interceptor.interceptModel(request, handler));
        assertEquals(ErrorCode.MODEL_RATE_LIMITED, thrown.getErrorCode());
    }

    @Test
    void normalReply_passesThrough() {
        SwallowedLlmErrorInterceptor interceptor = new SwallowedLlmErrorInterceptor();
        ModelResponse ok = ModelResponse.of(new AssistantMessage("合同主体为甲方"));
        ModelCallHandler handler = req -> ok;
        ModelRequest request = ModelRequest.builder()
                .messages(List.of(new UserMessage("合同基本信息")))
                .build();

        assertSame(ok, interceptor.interceptModel(request, handler));
    }
}
