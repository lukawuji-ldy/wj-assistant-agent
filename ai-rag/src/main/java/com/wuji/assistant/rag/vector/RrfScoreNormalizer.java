package com.wuji.assistant.rag.vector;

/**
 * RRF 分数归一化到 [0,1]：{@code normalized = rrfScore * (rankConstant + 1) / 2}。
 *
 * @author liudy
 */
public final class RrfScoreNormalizer {

    private RrfScoreNormalizer() {
    }

    /**
     * @param rrfScore     RRF 原始分（客户端 {@link ClientRrfFusion} 或集群原生 RRF）
     * @param rankConstant rank_constant（默认 60）
     * @return 归一化分 ∈ [0,1]
     */
    public static double normalize(double rrfScore, int rankConstant) {
        if (rankConstant < 0) {
            rankConstant = 60;
        }
        return rrfScore * (rankConstant + 1.0) / 2.0;
    }
}
