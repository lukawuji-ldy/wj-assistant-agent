package com.wuji.assistant.memory.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L1 Detector 单测。
 *
 * @author liudy
 */
class ExplicitRememberDetectorTest {

    @Test
    void detect_rememberAndAlways() {
        assertEquals("我用 Java 17", ExplicitRememberDetector.detectContent("记住：我用 Java 17"));
        assertNotNull(ExplicitRememberDetector.detectContent("以后都用中文回复"));
        assertTrue(ExplicitRememberDetector.matches("请记住我的偏好"));
        assertNull(ExplicitRememberDetector.detectContent("今天天气怎么样"));
    }
}
