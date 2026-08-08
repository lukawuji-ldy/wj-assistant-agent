package com.wuji.assistant.server.admin.log.audit;

import java.time.OffsetDateTime;

/**
 * 管理员操作日志列表行（不含 detail）。
 *
 * @author liudy
 */
public record AdminAuditLogSummary(
        String id,
        String adminId,
        String adminUsername,
        String action,
        String resourceType,
        String resourceId,
        OffsetDateTime createTime
) {
}
