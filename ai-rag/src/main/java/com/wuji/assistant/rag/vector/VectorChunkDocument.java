package com.wuji.assistant.rag.vector;

import java.util.UUID;

/**
 * 向量索引文档（PG 列或 ES 投影）。
 *
 * @author liudy
 */
public record VectorChunkDocument(
        UUID chunkId,
        String docId,
        long versionId,
        String collection,
        String status,
        String versionStatus,
        int revision,
        String contentHash,
        String section,
        String summary,
        String content,
        float[] embedding
) {
}
