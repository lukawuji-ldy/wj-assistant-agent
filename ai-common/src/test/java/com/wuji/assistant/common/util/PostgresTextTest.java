package com.wuji.assistant.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PostgreSQL 文本/JSONB 入参清洗单测。
 *
 * @author liudy
 */
class PostgresTextTest {

    @Test
    void sanitize_stripsNulBytes() {
        assertNull(PostgresText.sanitize(null));
        assertEquals("", PostgresText.sanitize(""));
        assertEquals("hello", PostgresText.sanitize("hello"));
        assertEquals("ab", PostgresText.sanitize("a\u0000b"));
        assertEquals("</mm:think>蓝色", PostgresText.sanitize("\u0000</mm:think>蓝色\u0000"));
    }

    @Test
    void sanitizeJson_stripsNulAndUnicodeEscape() {
        assertNull(PostgresText.sanitizeJson(null));
        assertEquals("{\"content\":\"ab\"}", PostgresText.sanitizeJson("{\"content\":\"a\u0000b\"}"));
        // 模拟 Jackson 已写出的 \u0000 转义（Java 字面量 "\\u0000" = 反斜杠 + u0000）
        assertEquals("{\"content\":\"</mm:think>1+1=2\"}",
                PostgresText.sanitizeJson("{\"content\":\"</mm:think>\\u00001+1=2\"}"));
    }
}
