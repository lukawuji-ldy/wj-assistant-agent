package com.wuji.assistant.rag.ingest;

/**
 * Chunk revision 视图。
 *
 * @param chunkId     逻辑 UUID
 * @param revision    revision 号
 * @param contentHash SHA-256 hex
 * @param status      ACTIVE|DEPRECATED
 * @param content     正文（列表可截断由调用方决定；此处完整）
 * @param createTime  ISO-8601
 * @author liudy
 */
public record KbChunkRevisionView(
        String chunkId,
        int revision,
        String contentHash,
        String status,
        String content,
        String createTime
) {
}
