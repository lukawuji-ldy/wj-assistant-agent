package com.wuji.assistant.rag.vector.es;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ES index mapping JSON 单测。
 */
class ElasticsearchIndexInitializerTest {

    @Test
    void indexJsonContainsIkAndDims() {
        String json = ElasticsearchIndexInitializer.buildIndexJson(1024);
        assertTrue(json.contains("ik_max_word"));
        assertTrue(json.contains("ik_smart"));
        assertTrue(json.contains("\"dims\": 1024"));
    }

    @Test
    void indexJsonSupportsDefaultOpenAiDims() {
        String json = ElasticsearchIndexInitializer.buildIndexJson(1536);
        assertTrue(json.contains("\"dims\": 1536"));
    }
}
