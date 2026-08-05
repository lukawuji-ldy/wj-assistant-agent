package com.wuji.assistant.server.mcp;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WujiWebFluxSseClientTransport} 单测。
 *
 * @author liudy
 */
class WujiWebFluxSseClientTransportTest {

    @Test
    void acceptEndpointEvent_firstSucceeds() {
        Sinks.One<String> sink = Sinks.one();
        assertTrue(WujiWebFluxSseClientTransport.acceptEndpointEvent(sink, "/mcp/message?sessionId=a"));
        assertEquals("/mcp/message?sessionId=a", sink.asMono().block());
    }

    @Test
    void acceptEndpointEvent_duplicateDoesNotFail() {
        Sinks.One<String> sink = Sinks.one();
        assertTrue(WujiWebFluxSseClientTransport.acceptEndpointEvent(sink, "/mcp/message?sessionId=a"));
        assertTrue(WujiWebFluxSseClientTransport.acceptEndpointEvent(sink, "/mcp/message?sessionId=b"));
        assertEquals("/mcp/message?sessionId=a", sink.asMono().block());
    }
}
