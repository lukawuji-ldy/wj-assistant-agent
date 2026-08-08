package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 向量块视图（管理台）。
 *
 * @param id               kb_chunk.chunk_id（逻辑 UUID）
 * @param chunkKey         展示键
 * @param chunkSeq         同版本内序号（1-based）
 * @param ingestedAt       当前 revision 生效时间 ISO-8601（毫秒）
 * @param content          ACTIVE revision 正文
 * @param section          章节
 * @param summary          摘要
 * @param status           ACTIVE|DEPRECATED
 * @param collection       集合
 * @param docId            文档
 * @param versionId        版本主键（JSON 序列化为字符串）
 * @param version          版本号
 * @param currentRevision  当前 ACTIVE revision
 * @param contentHash      当前 ACTIVE revision hash
 * @author liudy
 */
public record KbChunkView(
        String id,
        String chunkKey,
        Integer chunkSeq,
        String ingestedAt,
        String content,
        String section,
        String summary,
        String status,
        String collection,
        String docId,
        @JsonSerialize(using = ToStringSerializer.class) Long versionId,
        String version,
        Integer currentRevision,
        String contentHash
) {
}
