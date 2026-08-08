package com.wuji.assistant.server.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.server.admin.log.audit.AdminAuditLogDetail;
import com.wuji.assistant.server.admin.log.audit.AdminAuditLogQuery;
import com.wuji.assistant.server.admin.log.audit.AdminAuditLogSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 管理写操作审计落库与只读查询。
 *
 * @author liudy
 */
@Repository
public class AdminAuditLogRepository {

    private static final String SUMMARY_COLS = """
            a.id, a.admin_id, u.username AS admin_username, a.action, a.resource_type, a.resource_id, a.create_time
            """;

    private static final String FROM_JOIN = """
            FROM admin_audit_log a
            LEFT JOIN admin_user u ON u.admin_id = a.admin_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminAuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<AdminAuditLogSummary> summaryMapper = (rs, rowNum) -> new AdminAuditLogSummary(
            Long.toString(rs.getLong("id")),
            rs.getString("admin_id"),
            rs.getString("admin_username"),
            rs.getString("action"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            toOffset(rs.getTimestamp("create_time"))
    );

    /**
     * 写入一条审计。
     *
     * @param adminId      操作者
     * @param action       动作
     * @param resourceType 资源类型
     * @param resourceId   资源键
     * @param detail       详情（可为 null）
     */
    public void insert(String adminId, String action, String resourceType, String resourceId, Map<String, ?> detail) {
        String detailJson = null;
        if (detail != null && !detail.isEmpty()) {
            try {
                detailJson = objectMapper.writeValueAsString(detail);
            } catch (JsonProcessingException e) {
                detailJson = "{}";
            }
        }
        jdbcTemplate.update("""
                INSERT INTO admin_audit_log
                (id, admin_id, action, resource_type, resource_id, detail, create_time)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """,
                IdGenerator.nextLong(),
                adminId,
                action,
                resourceType,
                resourceId,
                detailJson,
                Timestamp.from(Instant.now()));
    }

    /**
     * 条件计数。
     */
    public long count(AdminAuditLogQuery query) {
        WhereClause where = buildWhere(query);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + FROM_JOIN + where.sql(),
                Long.class,
                where.args().toArray());
        return total == null ? 0L : total;
    }

    /**
     * 分页列表（不含 detail）。
     */
    public List<AdminAuditLogSummary> list(AdminAuditLogQuery query, int limit, int offset) {
        WhereClause where = buildWhere(query);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                "SELECT " + SUMMARY_COLS + " " + FROM_JOIN + where.sql()
                        + " ORDER BY a.create_time DESC LIMIT ? OFFSET ?",
                summaryMapper,
                args.toArray());
    }

    /**
     * 按主键查详情。
     */
    public Optional<AdminAuditLogDetail> findById(long id) {
        List<AdminAuditLogDetail> rows = jdbcTemplate.query("""
                SELECT a.id, a.admin_id, u.username AS admin_username, a.action, a.resource_type,
                       a.resource_id, a.detail, a.create_time
                """ + FROM_JOIN + """
                WHERE a.id = ?
                """, (rs, rowNum) -> new AdminAuditLogDetail(
                Long.toString(rs.getLong("id")),
                rs.getString("admin_id"),
                rs.getString("admin_username"),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                parseJson(rs.getObject("detail")),
                toOffset(rs.getTimestamp("create_time"))
        ), id);
        return rows.stream().findFirst();
    }

    /**
     * 构建 WHERE（供单测断言）。
     */
    public WhereClause buildWhere(AdminAuditLogQuery query) {
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendEq(sql, args, "a.admin_id", query.adminId());
        appendEq(sql, args, "a.action", query.action());
        appendEq(sql, args, "a.resource_type", query.resourceType());
        appendEq(sql, args, "a.resource_id", query.resourceId());
        if (query.createTimeFrom() != null) {
            appendClause(sql);
            sql.append("a.create_time >= ?");
            args.add(Timestamp.from(query.createTimeFrom()));
        }
        if (query.createTimeTo() != null) {
            appendClause(sql);
            sql.append("a.create_time <= ?");
            args.add(Timestamp.from(query.createTimeTo()));
        }
        return new WhereClause(sql.toString(), args);
    }

    private void appendEq(StringBuilder sql, List<Object> args, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        appendClause(sql);
        sql.append(column).append(" = ?");
        args.add(value.trim());
    }

    private static void appendClause(StringBuilder sql) {
        if (sql.isEmpty()) {
            sql.append(" WHERE ");
        } else {
            sql.append(" AND ");
        }
    }

    private JsonNode parseJson(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(raw.toString());
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(raw.toString());
        }
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    /**
     * WHERE 片段。
     */
    public record WhereClause(String sql, List<Object> args) {
    }
}
