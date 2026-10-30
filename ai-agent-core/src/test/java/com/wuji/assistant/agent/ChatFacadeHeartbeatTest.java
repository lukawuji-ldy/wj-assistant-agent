package com.wuji.assistant.agent;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 心跳不能把对话冷流再订一次。
 */
class ChatFacadeHeartbeatTest {

    @Test
    void withHeartbeat_subscribesMainOnceAndCompletes() {
        AtomicInteger subscriptions = new AtomicInteger();
        Flux<ServerSentEvent<String>> main = Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just(event("delta", "a"), event("done", "stop"));
        });

        List<ServerSentEvent<String>> events = ChatFacade.withHeartbeat(
                        main,
                        Duration.ofMinutes(1),
                        () -> event("ping", "p"))
                .collectList()
                .block(Duration.ofSeconds(3));

        assertEquals(1, subscriptions.get());
        assertEquals(List.of("delta", "done"), events.stream().map(ServerSentEvent::event).toList());
    }

    @Test
    void withHeartbeat_emitsPingThenStops() {
        AtomicInteger subscriptions = new AtomicInteger();
        Flux<ServerSentEvent<String>> main = Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.just(event("delta", "a"), event("done", "stop"))
                    .delayElements(Duration.ofMillis(120));
        });

        List<String> names = ChatFacade.withHeartbeat(
                        main,
                        Duration.ofMillis(40),
                        () -> event("ping", "p"))
                .map(ServerSentEvent::event)
                .collectList()
                .block(Duration.ofSeconds(3));

        assertEquals(1, subscriptions.get());
        assertTrue(names.contains("delta"));
        assertTrue(names.contains("done"));
        assertTrue(names.contains("ping"));
    }

    private static ServerSentEvent<String> event(String name, String data) {
        return ServerSentEvent.<String>builder().event(name).data(data).build();
    }
}
