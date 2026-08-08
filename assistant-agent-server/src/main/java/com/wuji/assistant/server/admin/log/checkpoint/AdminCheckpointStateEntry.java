package com.wuji.assistant.server.admin.log.checkpoint;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Checkpoint 顶层 state 表格行。
 *
 * @param key     键
 * @param type    类型
 * @param summary 摘要
 * @param value   完整 JSON（messages 行可为 null）
 * @author liudy
 */
public record AdminCheckpointStateEntry(
        String key,
        String type,
        String summary,
        JsonNode value
) {
}
