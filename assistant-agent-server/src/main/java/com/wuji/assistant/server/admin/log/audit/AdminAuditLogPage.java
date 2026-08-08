package com.wuji.assistant.server.admin.log.audit;

import java.util.List;

/**
 * 管理员操作日志分页。
 *
 * @author liudy
 */
public record AdminAuditLogPage(
        List<AdminAuditLogSummary> items,
        long total,
        int page,
        int size
) {
}
