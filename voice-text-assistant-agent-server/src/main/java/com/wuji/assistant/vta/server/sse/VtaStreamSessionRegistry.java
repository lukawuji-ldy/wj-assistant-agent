package com.wuji.assistant.vta.server.sse;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import com.wuji.assistant.agent.stream.StreamSession;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VTA SSE 会话（内存级，P0-first）。
 * <p>
 * 支持：
 * - session buffer：断线重连后按 Last-Event-ID 重放
 * - 同一 streamId 多次订阅：均可接收后续事件流
 */
public class VtaStreamSessionRegistry {

    public static class Entry {
        private final StreamSession session;
        private final Sinks.Many<ServerSentEvent<String>> sink;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean completed = new AtomicBoolean(false);

        Entry(StreamSession session) {
            this.session = session;
            this.sink = Sinks.many().multicast().onBackpressureBuffer();
        }

        public StreamSession session() {
            return session;
        }

        public Sinks.Many<ServerSentEvent<String>> sink() {
            return sink;
        }

        /**
         * 并行节点会并发 emit；multicast sink 遇 FAIL_NON_SERIALIZED 必须重试，否则事件只进 buffer 进不了 live。
         */
        public void emitNext(ServerSentEvent<String> event) {
            sink.emitNext(event, (signalType, result) -> result == Sinks.EmitResult.FAIL_NON_SERIALIZED);
        }

        public boolean markStarted() {
            return started.compareAndSet(false, true);
        }

        public void markCompleted() {
            completed.set(true);
        }

        public boolean isCompleted() {
            return completed.get();
        }
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxBufferEvents;

    public VtaStreamSessionRegistry(Duration ttl, int maxBufferEvents) {
        this.ttl = ttl;
        this.maxBufferEvents = maxBufferEvents;
    }

    public Entry getOrCreate(String streamId, String userId) {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(userId, "userId");
        return map.compute(streamId, (k, old) -> {
            if (old == null) {
                return new Entry(new StreamSession(streamId, userId, maxBufferEvents));
            }
            if (old.session().isExpired(ttl)) {
                return new Entry(new StreamSession(streamId, userId, maxBufferEvents));
            }
            return old;
        });
    }

    public Flux<ServerSentEvent<String>> stream(Entry entry, Long lastEventId) {
        Long after = lastEventId == null ? 0L : lastEventId;
        // 先订 live 再 merge replay，避免 concat 窗口内并行节点事件被 multicast 丢弃
        Flux<ServerSentEvent<String>> live = entry.sink().asFlux()
                .filter(ev -> parseId(ev.id()) > after);
        return Flux.defer(() -> {
            var replay = entry.session().replayAfter(after);
            Set<String> seen = ConcurrentHashMap.newKeySet();
            return Flux.merge(Flux.fromIterable(replay), live)
                    .filter(ev -> {
                        String id = ev.id();
                        if (id == null || id.isBlank()) {
                            return parseId(id) > after;
                        }
                        if (parseId(id) <= after) {
                            return false;
                        }
                        return seen.add(id);
                    });
        });
    }

    private static long parseId(String id) {
        if (id == null || id.isBlank()) return 0L;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

