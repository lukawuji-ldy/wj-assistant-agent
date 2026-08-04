package com.wuji.assistant.memory.retrieve;

/**
 * 长期记忆检索参数（由 core 配置传入）。
 *
 * @param topK               画像/偏好截断条数
 * @param weightSimilarity   相似度/问句相关性权重（profile=规则相关；semantic=余弦）
 * @param weightConfidence   置信度权重
 * @param weightFreshness    新鲜度权重
 * @param weightImportance   重要度权重
 * @param semanticEnabled    是否启用语义向量召回
 * @param semanticTopK       语义召回条数
 * @param semanticMinScore   语义最低余弦分
 * @author liudy
 */
public record MemoryRetrieveOptions(
        int topK,
        double weightSimilarity,
        double weightConfidence,
        double weightFreshness,
        double weightImportance,
        boolean semanticEnabled,
        int semanticTopK,
        double semanticMinScore
) {
    public static MemoryRetrieveOptions defaults() {
        return new MemoryRetrieveOptions(8, 0.5, 0.2, 0.2, 0.2, true, 4, 0.55);
    }

    public MemoryRetrieveOptions {
        if (topK <= 0) {
            topK = 8;
        }
        if (semanticTopK <= 0) {
            semanticTopK = 4;
        }
    }
}
