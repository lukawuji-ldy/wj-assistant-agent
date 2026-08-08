package com.wuji.assistant.server.admin.memory;

import java.time.Instant;

/**
 * Profile 管理视图。
 *
 * @author liudy
 */
public record AdminProfileView(
        String memoryId,
        String userId,
        String memoryType,
        String memoryKey,
        String memoryValue,
        String status,
        float confidence,
        float importance,
        String source,
        int version,
        Instant expireTime,
        Instant lastUsedTime,
        Instant createTime,
        Instant updateTime
) {
}
