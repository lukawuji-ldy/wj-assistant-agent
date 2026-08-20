package com.wuji.assistant.rag.ingest;

import com.wuji.assistant.rag.config.RagVectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * 批量 Embedding（{@code embedVersion}）固定间隔 + 429 指数退避。
 * 仅用于入库/重建，不作用于单 chunk 刷新。
 *
 * @author liudy
 */
final class EmbeddingBulkThrottle {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBulkThrottle.class);

    private final long requestIntervalMs;
    private final int maxRetriesOn429;
    private final long retryBackoffMs;
    private final long retryBackoffMaxMs;
    private final LongConsumer sleeper;
    private final AtomicLong lastSuccessEpochMs = new AtomicLong(0L);

    EmbeddingBulkThrottle(RagVectorProperties.Embedding props) {
        this(props, ms -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Embedding bulk throttle interrupted", e);
            }
        });
    }

    EmbeddingBulkThrottle(RagVectorProperties.Embedding props, LongConsumer sleeper) {
        Objects.requireNonNull(props, "props");
        this.requestIntervalMs = Math.max(0L, props.getRequestIntervalMs());
        this.maxRetriesOn429 = Math.max(0, props.getMaxRetriesOn429());
        this.retryBackoffMs = Math.max(0L, props.getRetryBackoffMs());
        this.retryBackoffMaxMs = Math.max(this.retryBackoffMs, props.getRetryBackoffMaxMs());
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    void awaitInterval() {
        if (requestIntervalMs <= 0L) {
            return;
        }
        long last = lastSuccessEpochMs.get();
        if (last <= 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - last;
        long wait = requestIntervalMs - elapsed;
        if (wait > 0L) {
            sleeper.accept(wait);
        }
    }

    void markSuccess() {
        lastSuccessEpochMs.set(System.currentTimeMillis());
    }

    /**
     * @param attempt 已失败次数（从 0 起）；若还可重试则 sleep 并返回 true
     */
    boolean backoffAndRetry(int attempt, String chunkId) {
        if (attempt >= maxRetriesOn429) {
            return false;
        }
        long delay = computeBackoffMs(attempt);
        log.warn("embedding rate-limited chunkId={} attempt={}/{} backoffMs={}",
                chunkId, attempt + 1, maxRetriesOn429, delay);
        if (delay > 0L) {
            sleeper.accept(delay);
        }
        return true;
    }

    long computeBackoffMs(int attempt) {
        if (retryBackoffMs <= 0L) {
            return 0L;
        }
        long factor = 1L << Math.min(attempt, 20);
        long delay = retryBackoffMs * factor;
        return Math.min(delay, retryBackoffMaxMs);
    }

    static boolean isRateLimitError(Throwable error) {
        Throwable t = error;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(Locale.ROOT);
                if (lower.contains("429")
                        || lower.contains("ratelimit")
                        || lower.contains("rate limit")
                        || lower.contains("qpm limit")
                        || lower.contains("too many requests")
                        || lower.contains("请求过于频繁")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }
}
