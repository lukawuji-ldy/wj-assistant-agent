package com.wuji.assistant.rag.vector;

import com.wuji.assistant.rag.RetrievalResult;
import com.wuji.assistant.rag.diagnose.RetrievalTestOptions;
import com.wuji.assistant.rag.diagnose.RetrievalTrace;

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
     * 限定领域检索。domain 为空时与 {@link #search(String, int, double)} 相同。
     *
     * @param domain {@code kb_chunk.domain_name}
     */
    default RetrievalResult search(String query, int topK, double minReliableScore, String domain) {
        return search(query, topK, minReliableScore);
    }

    /**
     * 检索诊断：返回阈值过滤前的向量路、普通路原始结果与 RRF 融合明细。
     * <p>
     * 必须与 {@link #search} 共享底层查询与融合函数，不改变线上 score/阈值语义。
     *
     * @param query   查询
     * @param domain  领域（直接模式必填）
     * @param options 临时参数覆盖；{@code null} 用后端默认
     */
    default RetrievalTrace diagnose(String query, String domain, RetrievalTestOptions options) {
        throw new UnsupportedOperationException("当前后端不支持检索诊断：" + vectorBackend());
    }

    /**
     * 把一份文档的向量投影改到新领域。PG 模式只改表，这里为空操作。
     */
    default void assignDomain(String docId, String domainName) {
    }

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
