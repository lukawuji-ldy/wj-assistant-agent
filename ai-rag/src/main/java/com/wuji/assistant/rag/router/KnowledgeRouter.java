package com.wuji.assistant.rag.router;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 用查询向量与各知识库说明向量的余弦相似度选 Top N。低于阈值的库不选。
 *
 * @author liudy
 */
public final class KnowledgeRouter {

    private KnowledgeRouter() {
    }

    /**
     * @param collection 知识库命名空间，对应 {@code kb_document.collection}
     * @param embedding  说明文本的向量
     */
    public record Profile(String collection, float[] embedding) {
    }

    /**
     * @param collection 选中的知识库
     * @param score      余弦相似度
     */
    public record Routed(String collection, double score) {
    }

    /**
     * @param query    查询向量
     * @param profiles 候选库
     * @param topN     最多返回几条
     * @param minScore 最低余弦；低于此值丢弃
     * @return 按相似度从高到低
     */
    public static List<Routed> select(float[] query, List<Profile> profiles, int topN, double minScore) {
        if (query == null || query.length == 0 || profiles == null || profiles.isEmpty() || topN <= 0) {
            return List.of();
        }
        List<Routed> scored = new ArrayList<>();
        for (Profile profile : profiles) {
            if (profile == null || !StringUtils.hasText(profile.collection()) || profile.embedding() == null) {
                continue;
            }
            double score = cosine(query, profile.embedding());
            if (score < minScore) {
                continue;
            }
            scored.add(new Routed(profile.collection().trim(), score));
        }
        scored.sort(Comparator.comparingDouble(Routed::score).reversed().thenComparing(Routed::collection));
        if (scored.size() > topN) {
            return List.copyOf(scored.subList(0, topN));
        }
        return List.copyOf(scored);
    }

    /**
     * @param left  向量
     * @param right 向量
     * @return 余弦；维度不同或零向量时为 0
     */
    public static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * (double) right[i];
            leftNorm += left[i] * (double) left[i];
            rightNorm += right[i] * (double) right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
