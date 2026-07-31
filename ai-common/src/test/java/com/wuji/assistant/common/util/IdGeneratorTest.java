package com.wuji.assistant.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IdGenerator 单元测试。
 *
 * @author liudy
 */
class IdGeneratorTest {

    @Test
    void nextBizIdHasPrefix() {
        String id = IdGenerator.nextBizId("c_");
        assertTrue(id.startsWith("c_"));
        assertTrue(id.length() > 3);
    }

    @Test
    void nextLongPositive() {
        assertTrue(IdGenerator.nextLong() > 0);
    }
}
