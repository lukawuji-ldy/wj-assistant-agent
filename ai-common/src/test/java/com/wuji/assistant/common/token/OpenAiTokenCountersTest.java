package com.wuji.assistant.common.token;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型名与 token_encoding 列到 tiktoken 词表的映射。
 *
 * @author liudy
 */
class OpenAiTokenCountersTest {

    @Test
    void columnOverridesModelName() {
        OpenAiTokenCounters.Selection selection = OpenAiTokenCounters.select("qwen-plus", "o200k_base");
        assertEquals(OpenAiTokenCounters.O200K_BASE, selection.encoding());
        assertTrue(selection.exactMatch());
    }

    @Test
    void gpt4oMiniUsesO200k() {
        OpenAiTokenCounters.Selection selection = OpenAiTokenCounters.select("gpt-4o-mini", null);
        assertEquals(OpenAiTokenCounters.O200K_BASE, selection.encoding());
        assertTrue(selection.exactMatch());
    }

    @Test
    void gpt4UsesCl100k() {
        OpenAiTokenCounters.Selection selection = OpenAiTokenCounters.select("gpt-4-turbo", "  ");
        assertEquals(OpenAiTokenCounters.CL100K_BASE, selection.encoding());
        assertTrue(selection.exactMatch());
    }

    @Test
    void unknownModelFallsBackToCl100k() {
        OpenAiTokenCounters.Selection selection = OpenAiTokenCounters.select("qwen-plus", null);
        assertEquals(OpenAiTokenCounters.CL100K_BASE, selection.encoding());
        assertFalse(selection.exactMatch());
    }

    @Test
    void unrecognizedColumnFallsBackToCl100k() {
        OpenAiTokenCounters.Selection selection = OpenAiTokenCounters.select("gpt-4o", "tiktoken");
        assertEquals(OpenAiTokenCounters.CL100K_BASE, selection.encoding());
        assertFalse(selection.exactMatch());
    }

    @Test
    void emptyModelFallsBackToCl100k() {
        OpenAiTokenCounters.Selection selection = OpenAiTokenCounters.select(null, null);
        assertEquals(OpenAiTokenCounters.CL100K_BASE, selection.encoding());
        assertFalse(selection.exactMatch());
    }
}
