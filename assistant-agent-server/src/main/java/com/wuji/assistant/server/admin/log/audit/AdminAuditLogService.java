package com.wuji.assistant.server.admin.log.audit;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 管理台管理员操作日志只读查询。
 *
 * @author liudy
 */
@Service
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;

    public AdminAuditLogService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页列表。
     */
    public AdminAuditLogPage list(
            String adminId,
            String action,
            String resourceType,
            String resourceId,
            Instant createTimeFrom,
            Instant createTimeTo,
            int page,
            int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        if (createTimeFrom != null && createTimeTo != null && createTimeFrom.isAfter(createTimeTo)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "createTimeFrom 不能晚于 createTimeTo");
        }
        AdminAuditLogQuery query = new AdminAuditLogQuery(
                blankToNull(adminId),
                blankToNull(action),
                blankToNull(resourceType),
                blankToNull(resourceId),
                createTimeFrom,
                createTimeTo,
                p,
                s);
        long total = repository.count(query);
        return new AdminAuditLogPage(repository.list(query, s, (p - 1) * s), total, p, s);
    }

    /**
     * 详情。
     */
    public AdminAuditLogDetail get(String id) {
        if (!StringUtils.hasText(id)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "id 不能为空");
        }
        long pk;
        try {
            pk = Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "id 非法: " + id);
        }
        return repository.findById(pk)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "操作日志不存在: " + id));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
