package com.wuji.assistant.server.admin.log.checkpoint;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 单步 Checkpoint 明细（含解码后的 state）。
 *
 * @author liudy
 */
public record AdminCheckpointDetail(
        String checkpointId,
        String parentCheckpointId,
        String threadId,
        String nodeId,
        String nextNodeId,
        OffsetDateTime savedAt,
        String stateContentType,
        JsonNode stateData,
        JsonNode decodedState,
        List<AdminCheckpointStateEntry> stateEntries,
        List<AdminCheckpointMessageView> messages,
        String decodeError
) {
}
