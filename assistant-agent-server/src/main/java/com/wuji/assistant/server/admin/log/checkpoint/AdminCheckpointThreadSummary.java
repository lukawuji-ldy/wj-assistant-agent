package com.wuji.assistant.server.admin.log.checkpoint;

import java.time.OffsetDateTime;

/**
 * Checkpoint 线程列表行。
 *
 * @author liudy
 */
public record AdminCheckpointThreadSummary(
        String threadId,
        String threadName,
        String userId,
        String conversationId,
        boolean isReleased,
        long checkpointCount,
        OffsetDateTime lastSavedAt
) {
}
