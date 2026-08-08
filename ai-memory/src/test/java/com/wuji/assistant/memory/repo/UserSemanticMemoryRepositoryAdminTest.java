package com.wuji.assistant.memory.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UserSemanticMemoryRepository 向量字面量单测。
 *
 * @author liudy
 */
class UserSemanticMemoryRepositoryAdminTest {

    @Test
    void toVectorLiteral_formats() {
        assertEquals("[1.0,2.5]", UserSemanticMemoryRepository.toVectorLiteral(new float[]{1f, 2.5f}));
        assertEquals("[]", UserSemanticMemoryRepository.toVectorLiteral(null));
        assertEquals("[]", UserSemanticMemoryRepository.toVectorLiteral(new float[]{}));
    }
}
