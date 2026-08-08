package com.wuji.assistant.server.admin.log.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * 管理员操作日志详情（含 detail）。
 *
 * @author liudy
 */
public record AdminAuditLogDetail(
        String id,
        String adminId,
        String adminUsername,
        String action,
        String resourceType,
        String resourceId,
        JsonNode detail,
        OffsetDateTime createTime
) {
}
