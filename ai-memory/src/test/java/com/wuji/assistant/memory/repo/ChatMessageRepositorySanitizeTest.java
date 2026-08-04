package com.wuji.assistant.memory.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ChatMessageRepository 文本落库清洗单测。
 *
 * @author liudy
 */
class ChatMessageRepositorySanitizeTest {

    @Test
    void sanitizeForPostgres_stripsNulBytes() {
        assertEquals("", ChatMessageRepository.sanitizeForPostgres(null));
        assertEquals("hello", ChatMessageRepository.sanitizeForPostgres("hello"));
        assertEquals("ab", ChatMessageRepository.sanitizeForPostgres("a\u0000b"));
        assertEquals("</mm:think>蓝色", ChatMessageRepository.sanitizeForPostgres("\u0000</mm:think>蓝色\u0000"));
    }
}
