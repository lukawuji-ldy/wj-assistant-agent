package com.wuji.assistant.server.admin.user;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.server.admin.audit.AdminAuditDetail;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminUserService 内置约束与改密测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AdminAuditLogRepository auditLogRepository;

    private AdminUserService service;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AdminAuthUser operator = AdminAuthUser.of("a_op", "op", "SUPER_ADMIN");

    @BeforeEach
    void setUp() {
        service = new AdminUserService(jdbcTemplate, passwordEncoder, auditLogRepository);
    }

    @Test
    void builtinCannotDelete() {
        stubGetBuiltin();
        WujiException ex = assertThrows(WujiException.class, () -> service.delete(operator, "a_admin"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(jdbcTemplate, never()).update(ArgumentMatchers.contains("DISABLED"), any(), any());
    }

    @Test
    void builtinCannotChangeRole() {
        stubGetBuiltin();
        WujiException ex = assertThrows(WujiException.class, () ->
                service.update(operator, "a_admin", new AdminUserUpdateRequest(null, "OPERATOR", null)));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void builtinCannotDisable() {
        stubGetBuiltin();
        WujiException ex = assertThrows(WujiException.class, () ->
                service.update(operator, "a_admin", new AdminUserUpdateRequest(null, null, "DISABLED")));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void builtinCanChangePassword() {
        stubGetBuiltin();
        when(jdbcTemplate.update(anyString(), any(), any(), eq("a_admin"))).thenReturn(1);
        service.changePassword(operator, "a_admin", new AdminPasswordChangeRequest("newpass123"));
        verify(jdbcTemplate).update(anyString(), any(), any(), eq("a_admin"));
        ArgumentCaptor<Map<String, ?>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogRepository).insert(eq("a_op"), eq("CHANGE_PASSWORD"), eq("admin_user"),
                eq("a_admin"), detailCaptor.capture());
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detailCaptor.getValue().get("changes");
        assertEquals(1, changes.size());
        assertEquals("password", changes.get(0).get("field"));
        assertEquals(AdminAuditDetail.CHANGED, changes.get(0).get("to"));
    }

    @SuppressWarnings("unchecked")
    private void stubGetBuiltin() {
        AdminUserView builtin = new AdminUserView(
                "a_admin", "admin", "超级管理员", "SUPER_ADMIN", "ACTIVE", true);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("a_admin")))
                .thenReturn(List.of(builtin));
    }
}
