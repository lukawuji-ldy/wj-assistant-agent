package com.wuji.assistant.server.admin.auth;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后台账号登录。
 *
 * @author liudy
 */
@Service
public class AdminAuthService {

    private static final RowMapper<AdminRow> MAPPER = (rs, rowNum) -> new AdminRow(
            rs.getString("admin_id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getString("role"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtService adminJwtService;

    public AdminAuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AdminJwtService adminJwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminJwtService = adminJwtService;
    }

    /**
     * 用户名密码登录。
     *
     * @param request 登录请求
     * @return 登录响应
     */
    public AdminLoginResponse login(AdminLoginRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        List<AdminRow> rows = jdbcTemplate.query("""
                SELECT admin_id, username, password_hash, display_name, role, status
                FROM admin_user WHERE username = ?
                """, MAPPER, request.username().trim());
        if (rows.isEmpty()) {
            throw new WujiException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        AdminRow row = rows.get(0);
        if (!"ACTIVE".equalsIgnoreCase(row.status())) {
            throw new WujiException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        if (!passwordEncoder.matches(request.password(), row.passwordHash())) {
            throw new WujiException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        AdminAuthUser admin = AdminAuthUser.of(row.adminId(), row.username(), row.role());
        String token = adminJwtService.issueToken(admin);
        return new AdminLoginResponse(
                token, "Bearer", row.adminId(), row.username(), row.displayName(), row.role());
    }

    private record AdminRow(
            String adminId,
            String username,
            String passwordHash,
            String displayName,
            String role,
            String status
    ) {
    }
}
