package com.wuji.assistant.server.admin.auth;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.server.config.AdminJwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AdminJwtService 单元测试。
 *
 * @author liudy
 */
class AdminJwtServiceTest {

    private AdminJwtService adminJwtService;

    @BeforeEach
    void setUp() {
        AdminJwtProperties props = new AdminJwtProperties();
        props.setSecret("change-me-wuji-admin-jwt-secret-key-32bytes");
        props.setIssuer("wuji-assistant-admin");
        props.setExpireHours(24);
        adminJwtService = new AdminJwtService(props);
    }

    @Test
    void issueAndParseRoundTrip() {
        AdminAuthUser admin = AdminAuthUser.of("a_admin", "admin", "SUPER_ADMIN");
        String token = adminJwtService.issueToken(admin);
        assertTrue(token.length() > 20);
        AdminAuthUser parsed = adminJwtService.parse(token);
        assertEquals("a_admin", parsed.adminId());
        assertEquals("admin", parsed.username());
        assertEquals("SUPER_ADMIN", parsed.role());
        assertEquals(AdminAuthUser.TOKEN_TYPE_ADMIN, parsed.tokenType());
    }

    @Test
    void rejectTamperedToken() {
        AdminAuthUser admin = AdminAuthUser.of("a_admin", "admin", "SUPER_ADMIN");
        String token = adminJwtService.issueToken(admin) + "x";
        assertThrows(Exception.class, () -> adminJwtService.parse(token));
    }
}
