package com.wuji.assistant.server.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * 管理写操作审计落库。
 *
 * @author liudy
 */
@Repository
public class AdminAuditLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminAuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

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
}
