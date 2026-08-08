package com.wuji.assistant.server.admin.user;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.server.admin.audit.AdminAuditDetail;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 后台用户管理（增删改查 + 改密），内置账号受限。
 *
 * @author liudy
 */
@Service
public class AdminUserService {

    private static final Set<String> ROLES = Set.of("SUPER_ADMIN", "OPERATOR");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

    private static final RowMapper<AdminUserView> VIEW_MAPPER = (rs, rowNum) -> new AdminUserView(
            rs.getString("admin_id"),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getBoolean("is_builtin")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminUserService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AdminAuditLogRepository auditLogRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 分页列表。
     *
     * @param page 页码（从 1）
     * @param size 页大小
     * @return 分页
     */
    public AdminUserPage list(int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_user", Long.class);
        long t = total == null ? 0L : total;
        List<AdminUserView> items = jdbcTemplate.query("""
                SELECT admin_id, username, display_name, role, status, is_builtin
                FROM admin_user
                ORDER BY create_time ASC
                LIMIT ? OFFSET ?
                """, VIEW_MAPPER, s, (p - 1) * s);
        return new AdminUserPage(items, t, p, s);
    }

    /**
     * 按 adminId 查询。
     *
     * @param adminId 业务键
     * @return 视图
     */
    public AdminUserView get(String adminId) {
        return requireRow(adminId);
    }

    /**
     * 创建管理员。
     *
     * @param operator 操作者
     * @param request  请求
     * @return 新建视图
     */
    public AdminUserView create(AdminAuthUser operator, AdminUserCreateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.username())
                || !StringUtils.hasText(request.password())
                || !StringUtils.hasText(request.displayName())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "用户名、密码、展示名不能为空");
        }
        String role = normalizeRole(request.role());
        String username = request.username().trim();
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_user WHERE username = ?", Integer.class, username);
        if (exists != null && exists > 0) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        String adminId = IdGenerator.nextBizId("a_");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO admin_user
                (id, admin_id, username, password_hash, display_name, role, status, is_builtin, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', FALSE, ?, ?)
                """,
                IdGenerator.nextLong(),
                adminId,
                username,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                role,
                now,
                now);
        auditLogRepository.insert(
                operator.adminId(),
                "CREATE",
                "admin_user",
                adminId,
                AdminAuditDetail.builder()
                        .created("username", username)
                        .created("displayName", request.displayName().trim())
                        .created("role", role)
                        .created("status", "ACTIVE")
                        .sensitiveChanged("password")
                        .build());
        return get(adminId);
    }

    /**
     * 更新资料/状态/角色；内置账号禁止改角色与状态。
     *
     * @param operator 操作者
     * @param adminId  目标
     * @param request  请求
     * @return 更新后视图
     */
    public AdminUserView update(AdminAuthUser operator, String adminId, AdminUserUpdateRequest request) {
        if (request == null) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        AdminUserView current = requireRow(adminId);
        String beforeDisplayName = current.displayName();
        String beforeRole = current.role();
        String beforeStatus = current.status();
        String displayName = StringUtils.hasText(request.displayName())
                ? request.displayName().trim()
                : current.displayName();
        String role = current.role();
        String status = current.status();
        if (StringUtils.hasText(request.role())) {
            if (current.builtin()) {
                throw new WujiException(ErrorCode.FORBIDDEN, "内置管理员不可修改角色");
            }
            role = normalizeRole(request.role());
        }
        if (StringUtils.hasText(request.status())) {
            if (current.builtin()) {
                throw new WujiException(ErrorCode.FORBIDDEN, "内置管理员不可禁用");
            }
            status = normalizeStatus(request.status());
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE admin_user
                SET display_name = ?, role = ?, status = ?, update_time = ?
                WHERE admin_id = ?
                """,
                displayName, role, status, now, adminId);
        auditLogRepository.insert(
                operator.adminId(),
                "UPDATE",
                "admin_user",
                adminId,
                AdminAuditDetail.builder()
                        .change("displayName", beforeDisplayName, displayName)
                        .change("role", beforeRole, role)
                        .change("status", beforeStatus, status)
                        .build());
        return get(adminId);
    }

    /**
     * 改密（含内置账号）。
     *
     * @param operator 操作者
     * @param adminId  目标
     * @param request  新密码
     */
    public void changePassword(AdminAuthUser operator, String adminId, AdminPasswordChangeRequest request) {
        if (request == null || !StringUtils.hasText(request.newPassword())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "新密码不能为空");
        }
        if (request.newPassword().length() < 6) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "密码至少 6 位");
        }
        requireRow(adminId);
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "UPDATE admin_user SET password_hash = ?, update_time = ? WHERE admin_id = ?",
                passwordEncoder.encode(request.newPassword()), now, adminId);
        auditLogRepository.insert(
                operator.adminId(),
                "CHANGE_PASSWORD",
                "admin_user",
                adminId,
                AdminAuditDetail.builder()
                        .sensitiveChanged("password")
                        .build());
    }

    /**
     * 禁用（软删）；内置拒绝。
     *
     * @param operator 操作者
     * @param adminId  目标
     */
    public void delete(AdminAuthUser operator, String adminId) {
        AdminUserView current = requireRow(adminId);
        if (current.builtin()) {
            throw new WujiException(ErrorCode.FORBIDDEN, "内置管理员不可删除");
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "UPDATE admin_user SET status = 'DISABLED', update_time = ? WHERE admin_id = ?",
                now, adminId);
        auditLogRepository.insert(
                operator.adminId(),
                "DELETE",
                "admin_user",
                adminId,
                AdminAuditDetail.builder()
                        .change("status", current.status(), "DISABLED")
                        .build());
    }

    private AdminUserView requireRow(String adminId) {
        if (!StringUtils.hasText(adminId)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "adminId 不能为空");
        }
        List<AdminUserView> rows = jdbcTemplate.query("""
                SELECT admin_id, username, display_name, role, status, is_builtin
                FROM admin_user WHERE admin_id = ?
                """, VIEW_MAPPER, adminId.trim());
        if (rows.isEmpty()) {
            throw new WujiException(ErrorCode.NOT_FOUND, "管理员不存在");
        }
        return rows.get(0);
    }

    private static String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "OPERATOR";
        }
        String r = role.trim().toUpperCase();
        if (!ROLES.contains(r)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "角色须为 SUPER_ADMIN 或 OPERATOR");
        }
        return r;
    }

    private static String normalizeStatus(String status) {
        String s = status.trim().toUpperCase();
        if (!STATUSES.contains(s)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "状态须为 ACTIVE 或 DISABLED");
        }
        return s;
    }
}
