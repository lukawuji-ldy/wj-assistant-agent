package com.wuji.assistant.vta.server.sse;

import com.wuji.assistant.agent.stream.StreamSession;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import org.springframework.http.codec.ServerSentEvent;

import java.time.Duration;

class VtaStreamSessionRegistryTest {

    @Test
    void replayFiltersByLastEventId() {
        VtaStreamSessionRegistry registry = new VtaStreamSessionRegistry(Duration.ofMinutes(10), 10);
        VtaStreamSessionRegistry.Entry entry = registry.getOrCreate("s1", "u1");

        ServerSentEvent<String> e1 = entry.session().append("customerTag", "{\"a\":1}");
        ServerSentEvent<String> e2 = entry.session().append("salesTag", "{\"b\":2}");

        // 不依赖 sink：回放应来自 session buffer
        Flux<ServerSentEvent<String>> stream = registry.stream(entry, 1L);

        StepVerifier.create(stream)
                .expectNextMatches(e -> "salesTag".equals(e.event()) && e.id().equals(e2.id()))
                .thenCancel()
                .verify();
    }

    @Test
    void liveEventsArePushed() {
        VtaStreamSessionRegistry registry = new VtaStreamSessionRegistry(Duration.ofMinutes(10), 10);
        VtaStreamSessionRegistry.Entry entry = registry.getOrCreate("s2", "u1");

        Flux<ServerSentEvent<String>> stream = registry.stream(entry, 0L);

        StepVerifier.create(stream.take(1))
                .then(() -> {
                    ServerSentEvent<String> e1 = entry.session().append("customerTag", "{\"a\":1}");
                    entry.emitNext(e1);
                })
                .expectNextMatches(e -> "customerTag".equals(e.event()))
                .thenCancel()
                .verify();
    }

    @Test
    void concurrentEmitNext_doesNotDropEvents() {
        VtaStreamSessionRegistry registry = new VtaStreamSessionRegistry(Duration.ofMinutes(10), 20);
        VtaStreamSessionRegistry.Entry entry = registry.getOrCreate("s3", "u1");

        Flux<ServerSentEvent<String>> stream = registry.stream(entry, 0L);

        StepVerifier.create(stream.take(4))
                .then(() -> {
                    java.util.List<Thread> threads = new java.util.ArrayList<>();
                    for (String name : java.util.List.of("customerTag", "salesTag", "callSummary", "intentScore")) {
                        Thread t = new Thread(() -> {
                            ServerSentEvent<String> ev = entry.session().append(name, "{\"ok\":true}");
                            entry.emitNext(ev);
                        });
                        threads.add(t);
                    }
                    threads.forEach(Thread::start);
                    threads.forEach(t -> {
                        try {
                            t.join();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                })
                .expectNextCount(4)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }
}

