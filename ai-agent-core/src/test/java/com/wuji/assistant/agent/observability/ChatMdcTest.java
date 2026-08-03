package com.wuji.assistant.agent.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ChatMdc 单测。
 *
 * @author liudy
 */
class ChatMdcTest {

    @AfterEach
    void tearDown() {
        ChatMdc.clear();
    }

    @Test
    void put_and_clear() {
        ChatMdc.put("t1", "c1", "u1", "a1");
        assertEquals("t1", MDC.get(ChatMdc.TRACE_ID));
        assertEquals("c1", MDC.get(ChatMdc.CONVERSATION_ID));
        assertEquals("u1", MDC.get(ChatMdc.USER_ID));
        assertEquals("a1", MDC.get(ChatMdc.AGENT_ID));
        ChatMdc.clear();
        assertNull(MDC.get(ChatMdc.TRACE_ID));
        assertNull(MDC.get(ChatMdc.CONVERSATION_ID));
    }
}
