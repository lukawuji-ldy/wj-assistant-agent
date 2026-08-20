package com.wuji.assistant.rag.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 客户端 Reciprocal Rank Fusion，公式与 ES 原生 RRF 一致：
 * {@code score(d) = Σ 1 / (rankConstant + rank_i(d))}，rank 为 1-based；
 * 未出现在某路结果中的文档对该路贡献为 0。
 * <p>
 * 用于 Basic 许可证等不支持 {@code retriever.rrf} 的集群，效果对齐原生 RRF。
 *
 * @author liudy
 */
public final class ClientRrfFusion {

    private ClientRrfFusion() {
    }

    /**
     * @param rankedLists  各路检索结果（列表顺序即排名，越前越好）
     * @param rankConstant RRF {@code rank_constant}（默认 60）
     * @param topK         融合后保留条数
     */
    public static List<FusedDoc> fuse(List<List<RankedDoc>> rankedLists, int rankConstant, int topK) {
        if (rankedLists == null || rankedLists.isEmpty() || topK <= 0) {
            return List.of();
        }
        int k = rankConstant < 0 ? 60 : rankConstant;
        Map<String, Acc> scores = new HashMap<>();
        for (List<RankedDoc> list : rankedLists) {
            if (list == null || list.isEmpty()) {
                continue;
            }
            int rank = 1;
            for (RankedDoc doc : list) {
                if (doc == null || doc.id() == null || doc.id().isBlank()) {
                    continue;
                }
                Acc acc = scores.computeIfAbsent(doc.id(), id -> new Acc());
                acc.rrfScore += 1.0 / (k + rank);
                if (acc.contentHash == null && doc.contentHash() != null && !doc.contentHash().isBlank()) {
                    acc.contentHash = doc.contentHash();
                }
                rank++;
            }
        }
        if (scores.isEmpty()) {
            return List.of();
        }
        List<FusedDoc> fused = new ArrayList<>(scores.size());
        for (Map.Entry<String, Acc> e : scores.entrySet()) {
            double rrf = e.getValue().rrfScore;
            fused.add(new FusedDoc(
                    e.getKey(),
                    rrf,
                    RrfScoreNormalizer.normalize(rrf, k),
                    e.getValue().contentHash));
        }
        fused.sort(Comparator
                .comparingDouble(FusedDoc::rrfScore).reversed()
                .thenComparing(FusedDoc::id));
        if (fused.size() > topK) {
            return List.copyOf(fused.subList(0, topK));
        }
        return List.copyOf(fused);
    }

    /**
     * 单路检索中的文档（按列表顺序排名）。
     */
    public record RankedDoc(String id, String contentHash) {
        public RankedDoc {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * 融合后的文档（含原始 RRF 分与归一化分）。
     */
    public record FusedDoc(String id, double rrfScore, double normalizedScore, String contentHash) {
    }

    private static final class Acc {
        private double rrfScore;
        private String contentHash;
    }
}
