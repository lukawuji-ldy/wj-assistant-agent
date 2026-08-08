package com.wuji.assistant.server.admin.log.audit;

import java.time.Instant;

/**
 * 管理员操作日志查询条件。
 *
 * @author liudy
 */
public record AdminAuditLogQuery(
        String adminId,
        String action,
        String resourceType,
        String resourceId,
        Instant createTimeFrom,
        Instant createTimeTo,
        int page,
        int size
) {
}
