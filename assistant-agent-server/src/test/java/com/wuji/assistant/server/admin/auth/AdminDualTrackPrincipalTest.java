package com.wuji.assistant.server.admin.auth;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.server.admin.security.CurrentAdmin;
import com.wuji.assistant.server.auth.JwtService;
import com.wuji.assistant.server.config.AdminJwtProperties;
import com.wuji.assistant.server.config.JwtProperties;
import com.wuji.assistant.server.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 双轨 Principal 类型校验：CurrentAdmin / CurrentUser 互斥。
 *
 * @author liudy
 */
class AdminDualTrackPrincipalTest {

    private AdminJwtService adminJwtService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AdminJwtProperties adminProps = new AdminJwtProperties();
        adminProps.setSecret("change-me-wuji-admin-jwt-secret-key-32bytes");
        adminProps.setIssuer("wuji-assistant-admin");
        adminJwtService = new AdminJwtService(adminProps);

        JwtProperties userProps = new JwtProperties();
        userProps.setSecret("change-me-wuji-assistant-jwt-secret-key-32b");
        userProps.setIssuer("wuji-assistant");
        jwtService = new JwtService(userProps);
    }

    @Test
    void adminTokenParsedByAdminService() {
        String token = adminJwtService.issueToken(AdminAuthUser.of("a_1", "admin", "SUPER_ADMIN"));
        AdminAuthUser parsed = adminJwtService.parse(token);
        assertEquals("a_1", parsed.adminId());
    }

    @Test
    void userJwtRejectedByAdminService() {
        String userToken = jwtService.issueToken(
                new AuthUser("u_1", "chat", "昵称", "default", "user"));
        assertThrows(Exception.class, () -> adminJwtService.parse(userToken));
    }

    @Test
    void currentAdminRejectsAuthUserPrincipal() {
        var auth = new UsernamePasswordAuthenticationToken(
                new AuthUser("u_1", "chat", "n", "default", "user"), null, List.of());
        WujiException ex = assertThrows(WujiException.class, () ->
                CurrentAdmin.require()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                        .block());
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void currentUserRejectsAdminPrincipal() {
        var auth = new UsernamePasswordAuthenticationToken(
                AdminAuthUser.of("a_1", "admin", "SUPER_ADMIN"), null, List.of());
        WujiException ex = assertThrows(WujiException.class, () ->
                CurrentUser.require()
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                        .block());
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }
}
