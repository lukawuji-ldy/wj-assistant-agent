package com.wuji.assistant.server.admin.memory;

import java.time.Instant;
import java.util.List;

/**
 * Semantic 管理视图。
 *
 * @author liudy
 */
public record AdminSemanticView(
        String id,
        String userId,
        String content,
        String memoryType,
        String status,
        float importance,
        float confidence,
        List<String> tags,
        String source,
        String sourceMessageId,
        Instant expireTime,
        Instant lastUsedTime,
        Instant createTime,
        Instant updateTime,
        Double score
) {
}
