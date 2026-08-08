package com.wuji.assistant.server.admin.log.checkpoint;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * 库内原始 Checkpoint 行。
 *
 * @author liudy
 */
public record AdminCheckpointRaw(
        String checkpointId,
        String parentCheckpointId,
        String threadId,
        String nodeId,
        String nextNodeId,
        OffsetDateTime savedAt,
        String stateContentType,
        JsonNode stateData
) {
}
