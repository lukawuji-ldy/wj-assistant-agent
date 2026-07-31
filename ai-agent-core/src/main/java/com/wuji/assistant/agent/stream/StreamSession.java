package com.wuji.assistant.agent.stream;

import org.springframework.http.codec.ServerSentEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单次 SSE 流会话：单调 eventId + 有界缓冲，供断线续传重放。
 *
 * @author liudy
 */
public class StreamSession {

    private final String streamId;
    private final String userId;
    private final int maxBufferEvents;
    private final AtomicLong eventSeq = new AtomicLong(0);
    private final Deque<ServerSentEvent<String>> buffer = new ArrayDeque<>();
    private final Instant createdAt = Instant.now();
    private volatile Instant lastAccessAt = createdAt;

    /**
     * @param streamId        流业务键
     * @param userId          归属用户
     * @param maxBufferEvents 缓冲上限
     */
    public StreamSession(String streamId, String userId, int maxBufferEvents) {
        this.streamId = streamId;
        this.userId = userId;
        this.maxBufferEvents = Math.max(1, maxBufferEvents);
    }

    public String getStreamId() {
        return streamId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    /**
     * 刷新最近访问时间。
     */
    public void touch() {
        this.lastAccessAt = Instant.now();
    }

    /**
     * 是否已超过 TTL。
     *
     * @param ttl 保留时长
     * @return true 表示过期
     */
    public boolean isExpired(Duration ttl) {
        return Instant.now().isAfter(lastAccessAt.plus(ttl));
    }

    /**
     * 分配下一个 eventId 并缓冲事件。
     *
     * @param event 事件名
     * @param data  JSON 字符串
     * @return SSE
     */
    public synchronized ServerSentEvent<String> append(String event, String data) {
        long id = eventSeq.incrementAndGet();
        ServerSentEvent<String> sse = ServerSentEvent.<String>builder()
                .id(String.valueOf(id))
                .event(event)
                .data(data)
                .build();
        buffer.addLast(sse);
        while (buffer.size() > maxBufferEvents) {
            buffer.removeFirst();
        }
        touch();
        return sse;
    }

    /**
     * 重放 eventId 大于 lastEventId 的缓冲事件。
     *
     * @param lastEventId 客户端已收到的最大 id，null/0 表示从头重放缓冲内全部
     * @return 事件副本列表
     */
    public synchronized List<ServerSentEvent<String>> replayAfter(Long lastEventId) {
        touch();
        long after = lastEventId == null ? 0L : lastEventId;
        List<ServerSentEvent<String>> result = new ArrayList<>();
        for (ServerSentEvent<String> e : buffer) {
            long id = parseId(e.id());
            if (id > after) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * 当前已分配的最大 eventId。
     *
     * @return 序号
     */
    public long currentEventId() {
        return eventSeq.get();
    }

    private static long parseId(String id) {
        if (id == null || id.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
