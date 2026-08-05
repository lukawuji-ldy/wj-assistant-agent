package com.wuji.assistant.agent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * LlmExtraJson 解析测试。
 *
 * @author liudy
 */
class LlmExtraJsonTest {

    @Test
    void text_readsKnownKey() {
        assertEquals("/v1/embeddings",
                LlmExtraJson.text("{\"embeddings_path\":\"/v1/embeddings\",\"dimensions\":1536}", "embeddings_path"));
    }

    @Test
    void text_missingOrInvalid_returnsNull() {
        assertNull(LlmExtraJson.text(null, "embeddings_path"));
        assertNull(LlmExtraJson.text("{}", "embeddings_path"));
        assertNull(LlmExtraJson.text("not-json", "embeddings_path"));
    }
}
