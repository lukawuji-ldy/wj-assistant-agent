package com.wuji.assistant.server.admin.log.checkpoint;

import java.time.OffsetDateTime;

/**
 * Checkpoint 步骤摘要（不含 stateData）。
 *
 * @author liudy
 */
public record AdminCheckpointStepSummary(
        String checkpointId,
        String parentCheckpointId,
        String nodeId,
        String nextNodeId,
        OffsetDateTime savedAt,
        String stateContentType,
        Long deltaMs,
        int stepIndex
) {
}
