package com.wuji.assistant.agent.model;

import com.wuji.assistant.agent.config.WujiSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApiKeyCipherService 加解密与兼容性测试。
 *
 * @author liudy
 */
class ApiKeyCipherServiceTest {

    private ApiKeyCipherService service;

    @BeforeEach
    void setUp() {
        WujiSecurityProperties props = new WujiSecurityProperties();
        props.setApiKeySecret("unit-test-api-key-secret-material");
        service = new ApiKeyCipherService(props);
    }

    @Test
    void encryptDecryptRoundTrip() {
        String plain = "sk-test-secret-key-1234";
        String stored = service.encrypt(plain);
        assertTrue(stored.startsWith(ApiKeyCipherService.PREFIX_V1));
        assertEquals(plain, service.decrypt(stored));
    }

    @Test
    void decryptLegacyEncPrefix() {
        assertEquals("sk-legacy", service.decrypt("enc:sk-legacy"));
    }

    @Test
    void decryptPlaintext() {
        assertEquals("CHANGE_ME", service.decrypt("CHANGE_ME"));
    }

    @Test
    void maskShowsLastFour() {
        String stored = service.encrypt("sk-abcdefghijklmnop");
        assertEquals("******mnop", service.mask(stored));
    }
}
