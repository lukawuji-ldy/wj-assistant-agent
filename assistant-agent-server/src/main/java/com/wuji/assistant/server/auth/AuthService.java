package com.wuji.assistant.server.auth;

import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 预置账号登录。
 *
 * @author liudy
 */
@Service
public class AuthService {

    private static final RowMapper<UserRow> MAPPER = (rs, rowNum) -> new UserRow(
            rs.getString("user_id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("nickname"),
            rs.getString("tenant_id"),
            rs.getString("role"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 用户名密码登录。
     *
     * @param request 登录请求
     * @return 登录响应
     */
    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        List<UserRow> rows = jdbcTemplate.query("""
                SELECT user_id, username, password_hash, nickname, tenant_id, role, status
                FROM sys_user WHERE username = ?
                """, MAPPER, request.username().trim());
        if (rows.isEmpty()) {
            throw new WujiException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        UserRow row = rows.get(0);
        if (!"ACTIVE".equalsIgnoreCase(row.status())) {
            throw new WujiException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        if (!passwordEncoder.matches(request.password(), row.passwordHash())) {
            throw new WujiException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        AuthUser user = new AuthUser(row.userId(), row.username(), row.nickname(), row.tenantId(), row.role());
        String token = jwtService.issueToken(user);
        return new LoginResponse(token, "Bearer", user.userId(), user.username(), user.nickname(), user.role());
    }

    private record UserRow(
            String userId,
            String username,
            String passwordHash,
            String nickname,
            String tenantId,
            String role,
            String status
    ) {
    }
}
