package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 批量 Embedding（入库 / 重建）进度快照。
 *
 * @param versionId   文档版本
 * @param status      IDLE / RUNNING / SUCCEEDED / FAILED
 * @param total       ACTIVE chunk 总数
 * @param completed   成功写入向量的 chunk 数
 * @param processed   已处理 chunk 数（含跳过）
 * @param lastChunkId 最近处理的 chunk
 * @param message     限流等待 / 失败原因（可空）
 * @author liudy
 */
public record EmbeddingJobProgress(
        @JsonSerialize(using = ToStringSerializer.class) Long versionId,
        String status,
        int total,
        int completed,
        int processed,
        String lastChunkId,
        String message
) {
    public static final String IDLE = "IDLE";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";

    static EmbeddingJobProgress idle(long versionId) {
        return new EmbeddingJobProgress(versionId, IDLE, 0, 0, 0, null, null);
    }
}
