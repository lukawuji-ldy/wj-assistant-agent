package com.wuji.assistant.rag.ingest;

import com.wuji.assistant.rag.config.RagVectorProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批量 Embedding 限速与 429 判定单测。
 */
class EmbeddingBulkThrottleTest {

    @Test
    void isRateLimitError_detects429Payload() {
        RuntimeException ex = new RuntimeException(
                "429 - {\"code\":429,\"reason\":\"RateLimitExceeded\",\"message\":\"请求过于频繁,请稍后再试\","
                        + "\"metadata\":{\"reason\":\"qpm limit exceeded\"}}");
        assertTrue(EmbeddingBulkThrottle.isRateLimitError(ex));
    }

    @Test
    void isRateLimitError_detectsWrappedCause() {
        RuntimeException wrapped = new RuntimeException("outer",
                new RuntimeException("RateLimitExceeded: too many requests"));
        assertTrue(EmbeddingBulkThrottle.isRateLimitError(wrapped));
    }

    @Test
    void isRateLimitError_rejectsOtherErrors() {
        assertFalse(EmbeddingBulkThrottle.isRateLimitError(new RuntimeException("500 Internal")));
        assertFalse(EmbeddingBulkThrottle.isRateLimitError(null));
    }

    @Test
    void computeBackoffMs_doublesUntilCap() {
        RagVectorProperties.Embedding props = new RagVectorProperties.Embedding();
        props.setRetryBackoffMs(1000L);
        props.setRetryBackoffMaxMs(5000L);
        EmbeddingBulkThrottle throttle = new EmbeddingBulkThrottle(props, ms -> {
        });

        assertEquals(1000L, throttle.computeBackoffMs(0));
        assertEquals(2000L, throttle.computeBackoffMs(1));
        assertEquals(4000L, throttle.computeBackoffMs(2));
        assertEquals(5000L, throttle.computeBackoffMs(3));
    }

    @Test
    void awaitInterval_sleepsRemainingGap() {
        RagVectorProperties.Embedding props = new RagVectorProperties.Embedding();
        props.setRequestIntervalMs(200L);
        List<Long> sleeps = new ArrayList<>();
        EmbeddingBulkThrottle throttle = new EmbeddingBulkThrottle(props, sleeps::add);

        throttle.markSuccess();
        throttle.awaitInterval();

        assertEquals(1, sleeps.size());
        assertTrue(sleeps.get(0) > 0L && sleeps.get(0) <= 200L);
    }

    @Test
    void backoffAndRetry_respectsMaxRetries() {
        RagVectorProperties.Embedding props = new RagVectorProperties.Embedding();
        props.setMaxRetriesOn429(2);
        props.setRetryBackoffMs(10L);
        props.setRetryBackoffMaxMs(100L);
        AtomicInteger sleepCalls = new AtomicInteger();
        EmbeddingBulkThrottle throttle = new EmbeddingBulkThrottle(props, ms -> sleepCalls.incrementAndGet());

        assertTrue(throttle.backoffAndRetry(0, "c1"));
        assertTrue(throttle.backoffAndRetry(1, "c1"));
        assertFalse(throttle.backoffAndRetry(2, "c1"));
        assertEquals(2, sleepCalls.get());
    }
}
