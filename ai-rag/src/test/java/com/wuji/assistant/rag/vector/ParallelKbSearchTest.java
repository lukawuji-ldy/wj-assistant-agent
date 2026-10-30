package com.wuji.assistant.rag.vector;

import com.wuji.assistant.rag.KnowledgeRetrievalService;
import com.wuji.assistant.rag.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多知识库检索并行，总耗时接近单库而不是三库相加。
 *
 * @author liudy
 */
class ParallelKbSearchTest {

    @Test
    void threeCollectionsFinishNearSingleLatency() throws Exception {
        int delayMs = 180;
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(new SleepyPort(delayMs));
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            long start = System.nanoTime();
            RetrievalResult result = service.retrieveMany(
                    List.of("请假制度"),
                    List.of("kb-a", "kb-b", "kb-c"),
                    3,
                    0.2,
                    60,
                    pool);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertEquals(3, result.hits().size());
            assertTrue(elapsedMs < delayMs * 2L, "elapsedMs=" + elapsedMs);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private static final class SleepyPort implements VectorIndexPort {
        private final int delayMs;

        private SleepyPort(int delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public void upsertChunk(VectorChunkDocument document) {
        }

        @Override
        public void deprecateChunk(UUID chunkId) {
        }

        @Override
        public void deprecateVersion(long versionId) {
        }

        @Override
        public RetrievalResult search(String query, int topK, double minReliableScore) {
            return search(query, topK, minReliableScore, "all");
        }

        @Override
        public RetrievalResult search(String query, int topK, double minReliableScore, String collection) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new RetrievalResult(List.of(
                    new RetrievalResult.Hit(collection, collection, 0.9, Map.of("collection", collection))
            ), false, null);
        }

        @Override
        public int embeddedCount(long versionId) {
            return 0;
        }

        @Override
        public String vectorBackend() {
            return "test";
        }

        @Override
        public String indexName() {
            return null;
        }
    }
}
