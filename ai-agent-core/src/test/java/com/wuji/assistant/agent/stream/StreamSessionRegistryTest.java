package com.wuji.assistant.agent.stream;

import com.wuji.assistant.agent.config.WujiAgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StreamSession / Registry 单元测试。
 *
 * @author liudy
 */
class StreamSessionRegistryTest {

    private StreamSessionRegistry registry;

    @BeforeEach
    void setUp() {
        WujiAgentProperties props = new WujiAgentProperties();
        props.getStream().setResumeBufferEvents(3);
        props.getStream().setResumeTtl(Duration.ofMinutes(10));
        props.getStream().setHeartbeatInterval(Duration.ofSeconds(15));
        registry = new StreamSessionRegistry(props);
    }

    @Test
    void appendAssignsMonotonicIds_andBuffers() {
        StreamSession session = registry.create("u1");
        ServerSentEvent<String> e1 = session.append("meta", "{\"a\":1}");
        ServerSentEvent<String> e2 = session.append("delta", "{\"c\":\"x\"}");
        assertEquals("1", e1.id());
        assertEquals("2", e2.id());
        assertEquals(2, session.currentEventId());
    }

    @Test
    void replayAfterReturnsOnlyNewerEvents() {
        StreamSession session = registry.register("s_test", "u1");
        session.append("meta", "{}");
        session.append("delta", "{\"content\":\"a\"}");
        session.append("delta", "{\"content\":\"b\"}");
        List<ServerSentEvent<String>> replay = session.replayAfter(1L);
        assertEquals(2, replay.size());
        assertEquals("2", replay.get(0).id());
        assertEquals("3", replay.get(1).id());
    }

    @Test
    void bufferEvictsOldestWhenOverCapacity() {
        StreamSession session = registry.create("u1");
        session.append("e", "1");
        session.append("e", "2");
        session.append("e", "3");
        session.append("e", "4");
        List<ServerSentEvent<String>> all = session.replayAfter(0L);
        assertEquals(3, all.size());
        assertEquals("2", all.get(0).id());
        assertEquals("4", all.get(2).id());
    }

    @Test
    void findActiveRejectsWrongUserOrMissing() {
        StreamSession session = registry.create("u1");
        assertTrue(registry.findActive(session.getStreamId(), "u1").isPresent());
        assertFalse(registry.findActive(session.getStreamId(), "u2").isPresent());
        assertFalse(registry.findActive("s_missing", "u1").isPresent());
    }

    @Test
    void expiredSessionNotFound() {
        WujiAgentProperties props = new WujiAgentProperties();
        props.getStream().setResumeBufferEvents(10);
        props.getStream().setResumeTtl(Duration.ofMillis(1));
        StreamSessionRegistry shortTtl = new StreamSessionRegistry(props);
        StreamSession session = shortTtl.create("u1");
        session.append("meta", "{}");
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(shortTtl.findActive(session.getStreamId(), "u1").isPresent());
    }
}
