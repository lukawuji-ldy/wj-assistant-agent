package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmbeddingJobProgressTrackerTest {

    @Test
    void snapshotIdleWhenUnknown() {
        EmbeddingJobProgressTracker tracker = new EmbeddingJobProgressTracker();
        EmbeddingJobProgress snap = tracker.snapshot(9L);
        assertEquals(EmbeddingJobProgress.IDLE, snap.status());
        assertEquals(0, snap.total());
    }

    @Test
    void tickAdvancesProcessedAndCompleted() {
        EmbeddingJobProgressTracker tracker = new EmbeddingJobProgressTracker();
        tracker.start(1L, 3);
        tracker.tick(1L, "a", true);
        tracker.waiting(1L, "厂商限流，退避重试中");
        EmbeddingJobProgress waiting = tracker.snapshot(1L);
        assertEquals(EmbeddingJobProgress.RUNNING, waiting.status());
        assertEquals("厂商限流，退避重试中", waiting.message());

        tracker.tick(1L, "b", false);
        tracker.succeed(1L);
        EmbeddingJobProgress done = tracker.snapshot(1L);
        assertEquals(EmbeddingJobProgress.SUCCEEDED, done.status());
        assertEquals(3, done.total());
        assertEquals(1, done.completed());
        assertEquals(2, done.processed());
        assertEquals("b", done.lastChunkId());
        assertNull(done.message());
    }

    @Test
    void failRecordsMessage() {
        EmbeddingJobProgressTracker tracker = new EmbeddingJobProgressTracker();
        tracker.start(2L, 4);
        tracker.fail(2L, "429");
        EmbeddingJobProgress snap = tracker.snapshot(2L);
        assertEquals(EmbeddingJobProgress.FAILED, snap.status());
        assertEquals("429", snap.message());
    }
}
