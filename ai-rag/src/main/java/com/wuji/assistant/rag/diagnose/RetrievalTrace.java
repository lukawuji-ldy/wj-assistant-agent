package com.wuji.assistant.rag.diagnose;

import java.util.List;
import java.util.Map;

/**
 * 检索诊断轨迹。只读快照，用于管理台展示阈值过滤前的双路原始结果与 RRF 融合结果。
 * <p>
 * 不返回完整 query vector，只返回 embedding 模型标识、维度、可用性与耗时。
 *
 * @param backend          pgvector | elasticsearch
 * @param domain           检索领域（直接模式必填）
 * @param query            原始查询
 * @param effectiveOptions 本次实际生效的参数
 * @param embedding        embedding 摘要
 * @param vectorHits       向量路：PG=余弦，ES=kNN
 * @param keywordHits      普通路：PG=ILIKE，ES=BM25
 * @param fusedHits        融合后结果
 * @param warnings         降级/漂移等告警
 * @param durationMs       总耗时
 * @author liudy
 */
public record RetrievalTrace(
        String backend,
        String domain,
        String query,
        RetrievalTestOptions effectiveOptions,
        EmbeddingInfo embedding,
        List<RetrievalHit> vectorHits,
        List<RetrievalHit> keywordHits,
        List<FusedHit> fusedHits,
        List<String> warnings,
        long durationMs
) {
    /**
     * Embedding 摘要。不暴露向量本体与密钥。
     *
     * @param available    是否可用
     * @param configId     EMBEDDING 行 config_id
     * @param modelVersion 指纹 configId|model|dimensions
     * @param dimensions   维度
     * @param durationMs   embed 耗时
     */
    public record EmbeddingInfo(
            boolean available,
            String configId,
            String modelVersion,
            int dimensions,
            long durationMs
    ) {
    }

    /**
     * 单路命中。
     *
     * @param rank        本路 1-based 排名
     * @param rawScore    PG 余弦相似度 / ILIKE 排名分；ES kNN _score / BM25 _score
     */
    public record RetrievalHit(
            int rank,
            String chunkId,
            String docId,
            Long versionId,
            String version,
            String section,
            Integer revision,
            String contentHash,
            String content,
            Double rawScore,
            Map<String, Object> metadata
    ) {
    }

    /**
     * RRF 融合后命中。
     *
     * @param vectorRank         向量路 rank，未命中为 null
     * @param keywordRank        普通路 rank，未命中为 null
     * @param vectorContribution 向量路 RRF 贡献 1/(k+rank)，未命中为 0
     * @param keywordContribution 普通路 RRF 贡献
     * @param rrfScore           原始 RRF 分
     * @param normalizedRrf      归一化 RRF
     * @param businessScore      融合后业务 score（与线上 search 一致）
     * @param passedThreshold    businessScore >= minReliableScore
     */
    public record FusedHit(
            int rank,
            String chunkId,
            String docId,
            Long versionId,
            String version,
            String section,
            Integer revision,
            String contentHash,
            String content,
            Integer vectorRank,
            Integer keywordRank,
            Double vectorContribution,
            Double keywordContribution,
            double rrfScore,
            double normalizedRrf,
            double businessScore,
            boolean passedThreshold,
            Map<String, Object> metadata
    ) {
    }
}
