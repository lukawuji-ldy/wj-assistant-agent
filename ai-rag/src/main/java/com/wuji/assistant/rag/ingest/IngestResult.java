package com.wuji.assistant.rag.ingest;

/**
 * 入库结果。
 *
 * @param docId     文档
 * @param versionId 版本主键
 * @param version   版本号
 * @param chunkCount 切片数
 * @param embedded   是否写入了 embedding
 * @author liudy
 */
public record IngestResult(
        String docId,
        long versionId,
        String version,
        int chunkCount,
        boolean embedded
) {
}
