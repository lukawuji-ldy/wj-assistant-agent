package com.wuji.assistant.rag;

import com.wuji.assistant.rag.vector.VectorIndexPort;
import org.springframework.stereotype.Service;

/**
 * 知识库检索门面：委托 {@link VectorIndexPort}（PGVector 或 Elasticsearch）。
 *
 * @author liudy
 */
@Service
public class KnowledgeRetrievalService {

    private final VectorIndexPort vectorIndexPort;

    public KnowledgeRetrievalService(VectorIndexPort vectorIndexPort) {
        this.vectorIndexPort = vectorIndexPort;
    }

    public RetrievalResult retrieve(String query, int topK, double minReliableScore) {
        return vectorIndexPort.search(query, topK, minReliableScore);
    }

    /**
     * 合并余弦与关键词命中（PG 适配器单元测试用）。
     */
    static java.util.List<RetrievalResult.Hit> mergeHits(
            java.util.List<RetrievalResult.Hit> cosineHits,
            java.util.List<RetrievalResult.Hit> keywordHits,
            int limit) {
        return com.wuji.assistant.rag.vector.PgVectorIndexAdapter.mergeHits(cosineHits, keywordHits, limit);
    }

    /**
     * ILIKE 模式（PG 适配器单元测试用）。
     */
    static java.util.List<String> likePatterns(String query) {
        return com.wuji.assistant.rag.vector.PgVectorIndexAdapter.likePatterns(query);
    }
}
