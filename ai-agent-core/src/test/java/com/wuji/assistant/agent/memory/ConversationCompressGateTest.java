package com.wuji.assistant.agent.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一会话的压缩任务不并发。
 *
 * @author liudy
 */
class ConversationCompressGateTest {

    @Test
    void secondAcquire_waitsUntilRelease() {
        ConversationCompressGate gate = new ConversationCompressGate();
        assertTrue(gate.tryAcquire("c1"));
        assertFalse(gate.tryAcquire("c1"));
        gate.release("c1");
        assertTrue(gate.tryAcquire("c1"));
    }
}
