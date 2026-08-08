package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 入库结果。
 *
 * @param docId     文档
 * @param versionId 版本主键（JSON 序列化为字符串，避免 JS 丢精度）
 * @param version   版本号
 * @param chunkCount 切片数
 * @param embedded   是否写入了 embedding
 * @author liudy
 */
public record IngestResult(
        String docId,
        @JsonSerialize(using = ToStringSerializer.class) long versionId,
        String version,
        int chunkCount,
        boolean embedded
) {
}
