package com.wuji.assistant.rag.vector.es;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ES 异常根因摘取单测。
 */
class ElasticsearchIndexAdapterRootCauseTest {

    @Test
    void prefersDimensionMismatchMessageInCauseChain() {
        Throwable root = new IllegalArgumentException(
                "The query vector has a different number of dimensions [1024] than the document vectors [1536].");
        Throwable mid = new RuntimeException("failed to create query", root);
        Throwable top = new RuntimeException("[es/search] failed: all shards failed", mid);

        String detail = ElasticsearchIndexAdapter.rootCauseDetail(top);
        assertTrue(detail.contains("1024"));
        assertTrue(detail.contains("1536"));
        assertTrue(detail.toLowerCase().contains("dimensions"));
    }
}
