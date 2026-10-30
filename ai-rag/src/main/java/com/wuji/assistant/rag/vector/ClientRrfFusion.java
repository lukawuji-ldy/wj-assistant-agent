package com.wuji.assistant.rag.vector;

import java.util.ArrayList;
import java.util.Collections;
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
     * 融合明细：额外暴露每路的 1-based rank 与 contribution，供管理台检索诊断展示。
     * <p>
     * 只增不改：线上 {@link #fuse(List, int, int)} 行为不变。
     *
     * @param ranks         各路 rank（列表顺序对应 {@code rankedLists}，未命中为 null）
     * @param contributions 各路 RRF 贡献 1/(k+rank)，未命中为 0
     */
    public static List<DetailedFusedDoc> fuseDetailed(List<List<RankedDoc>> rankedLists, int rankConstant, int topK) {
        if (rankedLists == null || rankedLists.isEmpty() || topK <= 0) {
            return List.of();
        }
        int k = rankConstant < 0 ? 60 : rankConstant;
        int lanes = rankedLists.size();
        Map<String, DetailAcc> scores = new HashMap<>();
        for (int lane = 0; lane < lanes; lane++) {
            List<RankedDoc> list = rankedLists.get(lane);
            if (list == null || list.isEmpty()) {
                continue;
            }
            int rank = 1;
            for (RankedDoc doc : list) {
                if (doc == null || doc.id() == null || doc.id().isBlank()) {
                    continue;
                }
                DetailAcc acc = scores.computeIfAbsent(doc.id(), id -> new DetailAcc(lanes));
                double contribution = 1.0 / (k + rank);
                acc.rrfScore += contribution;
                acc.ranks[lane] = rank;
                acc.contributions[lane] = contribution;
                if (acc.contentHash == null && doc.contentHash() != null && !doc.contentHash().isBlank()) {
                    acc.contentHash = doc.contentHash();
                }
                rank++;
            }
        }
        if (scores.isEmpty()) {
            return List.of();
        }
        List<DetailedFusedDoc> fused = new ArrayList<>(scores.size());
        for (Map.Entry<String, DetailAcc> e : scores.entrySet()) {
            DetailAcc acc = e.getValue();
            fused.add(new DetailedFusedDoc(
                    e.getKey(),
                    toRankList(acc.ranks),
                    toContributionList(acc.contributions),
                    acc.rrfScore,
                    RrfScoreNormalizer.normalize(acc.rrfScore, k),
                    acc.contentHash));
        }
        fused.sort(Comparator
                .comparingDouble(DetailedFusedDoc::rrfScore).reversed()
                .thenComparing(DetailedFusedDoc::id));
        if (fused.size() > topK) {
            return List.copyOf(fused.subList(0, topK));
        }
        return List.copyOf(fused);
    }

    /**
     * 各路 rank 列表允许含 {@code null}（该路未命中），因此不能使用 {@link List#copyOf}。
     */
    private static List<Integer> toRankList(int[] ranks) {
        List<Integer> out = new ArrayList<>(ranks.length);
        for (int rank : ranks) {
            out.add(rank <= 0 ? null : rank);
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Double> toContributionList(double[] contributions) {
        List<Double> out = new ArrayList<>(contributions.length);
        for (double contribution : contributions) {
            out.add(contribution);
        }
        return List.copyOf(out);
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

    /**
     * 带每路 rank 与 contribution 的融合明细。
     */
    public record DetailedFusedDoc(
            String id,
            List<Integer> ranks,
            List<Double> contributions,
            double rrfScore,
            double normalizedScore,
            String contentHash) {
    }

    private static final class DetailAcc {
        private double rrfScore;
        private String contentHash;
        private final int[] ranks;
        private final double[] contributions;

        private DetailAcc(int lanes) {
            this.ranks = new int[lanes];
            this.contributions = new double[lanes];
        }
    }

    private static final class Acc {
        private double rrfScore;
        private String contentHash;
    }
}
