package com.wuji.assistant.rag.ingest;

/**
 * Chunk 写结果。
 *
 * @param view     视图
 * @param embedded 是否写入了 embedding
 * @author liudy
 */
public record KbChunkWriteResult(KbChunkView view, boolean embedded) {
}
