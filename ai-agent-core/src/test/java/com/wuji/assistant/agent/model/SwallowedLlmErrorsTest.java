package com.wuji.assistant.agent.model;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentLlmNode 把 LLM 异常写成 AssistantMessage 文本时的识别与还原。
 *
 * @author liudy
 */
class SwallowedLlmErrorsTest {

    private static final String RATE_LIMIT_TEXT =
            "Exception: 429 - {\"code\":429,\"reason\":\"RateLimitExceeded\","
                    + "\"message\":\"请求过于频繁,请稍后再试\"}";

    @Test
    void rateLimitText_isSwallowedAndMapsToRateLimited() {
        assertTrue(SwallowedLlmErrors.isSwallowed(RATE_LIMIT_TEXT));
        RuntimeException ex = SwallowedLlmErrors.toException(RATE_LIMIT_TEXT);
        assertInstanceOf(WujiException.class, ex);
        assertEquals(ErrorCode.MODEL_RATE_LIMITED, ((WujiException) ex).getErrorCode());
        assertTrue(ex.getMessage().contains("429"));
    }

    @Test
    void timeoutText_mapsToTimeout() {
        RuntimeException ex = SwallowedLlmErrors.toException("Exception: timeout waiting for response");
        assertInstanceOf(WujiException.class, ex);
        assertEquals(ErrorCode.MODEL_TIMEOUT, ((WujiException) ex).getErrorCode());
    }

    @Test
    void normalAssistantText_isNotSwallowed() {
        assertFalse(SwallowedLlmErrors.isSwallowed("根据合同基本信息，甲方为..."));
        assertNull(SwallowedLlmErrors.toException("根据合同基本信息，甲方为..."));
        assertNull(SwallowedLlmErrors.toException(null));
        assertNull(SwallowedLlmErrors.toException(""));
        assertNull(SwallowedLlmErrors.toException("The exception: user not found"));
    }
}
