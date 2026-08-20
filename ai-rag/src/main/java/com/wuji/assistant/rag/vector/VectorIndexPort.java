package com.wuji.assistant.rag.vector;

import com.wuji.assistant.rag.RetrievalResult;

import java.util.UUID;

/**
 * 知识库向量索引端口（PGVector 或 Elasticsearch 二选一）。
 *
 * @author liudy
 */
public interface VectorIndexPort {

    /**
     * 写入或更新 chunk 向量投影。
     */
    void upsertChunk(VectorChunkDocument document);

    /**
     * 停用单个 chunk 投影（不物理删）。
     */
    void deprecateChunk(UUID chunkId);

    /**
     * 停用版本下全部 chunk 投影。
     */
    void deprecateVersion(long versionId);

    /**
     * Hybrid / 关键词检索，返回水合后的 {@link RetrievalResult}。
     */
    RetrievalResult search(String query, int topK, double minReliableScore);

    /**
     * 当前版本已嵌入 chunk 数。
     */
    int embeddedCount(long versionId);

    /**
     * 当前后端标识：pgvector | elasticsearch。
     */
    String vectorBackend();

    /**
     * ES index 名；PG 模式返回 null。
     */
    String indexName();
}
