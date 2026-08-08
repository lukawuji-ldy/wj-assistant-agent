package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * ContentHashes 单测。
 *
 * @author liudy
 */
class ContentHashesTest {

    @Test
    void sha256HexStable() {
        assertEquals(ContentHashes.sha256Hex("hello"), ContentHashes.sha256Hex("hello"));
        assertNotEquals(ContentHashes.sha256Hex("hello"), ContentHashes.sha256Hex("world"));
        assertEquals(64, ContentHashes.sha256Hex("").length());
    }
}
