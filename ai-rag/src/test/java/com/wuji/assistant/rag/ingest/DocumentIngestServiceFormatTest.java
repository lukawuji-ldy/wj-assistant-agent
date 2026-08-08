package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入库时间格式（毫秒）单测。
 *
 * @author liudy
 */
class DocumentIngestServiceFormatTest {

    @Test
    void formatIngestedAt_truncatesToMillisIso() {
        Instant instant = Instant.parse("2026-08-07T10:11:12.345678Z");
        String formatted = DocumentIngestService.formatIngestedAt(instant);
        assertEquals("2026-08-07T10:11:12.345Z", formatted);
        assertTrue(formatted.endsWith("Z"));
    }
}
