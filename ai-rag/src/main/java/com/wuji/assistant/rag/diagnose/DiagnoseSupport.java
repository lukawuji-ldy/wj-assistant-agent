package com.wuji.assistant.rag.diagnose;

import com.wuji.assistant.rag.RetrievalResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索诊断轨迹构造工具。PG / ES 适配器共享，避免复制命中拆分与融合汇总逻辑。
 *
 * @author liudy
 */
public final class DiagnoseSupport {

    private DiagnoseSupport() {
    }

    /**
     * 按列表顺序生成带 1-based rank 的单路命中。
     */
    public static List<RetrievalTrace.RetrievalHit> toHits(List<RetrievalResult.Hit> hits, double[] rawScores) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<RetrievalTrace.RetrievalHit> out = new ArrayList<>(hits.size());
        int rank = 1;
        for (int i = 0; i < hits.size(); i++) {
            RetrievalResult.Hit hit = hits.get(i);
            if (hit == null || hit.chunkId() == null || hit.chunkId().isBlank()) {
                continue;
            }
            Double raw = rawScores != null && i < rawScores.length ? rawScores[i] : hit.score();
            out.add(toHit(rank++, hit, raw));
        }
        return List.copyOf(out);
    }

    public static RetrievalTrace.RetrievalHit toHit(int rank, RetrievalResult.Hit hit, Double rawScore) {
        Map<String, Object> meta = hit.metadata() == null ? Map.of() : hit.metadata();
        return new RetrievalTrace.RetrievalHit(
                rank,
                hit.chunkId(),
                stringOf(meta.get("doc_id")),
                longOf(meta.get("version_id")),
                stringOf(meta.get("version")),
                stringOf(meta.get("section")),
                intOf(meta.get("revision")),
                stringOf(meta.get("content_hash")),
                hit.content(),
                rawScore,
                meta);
    }

    /**
     * 构造融合后命中。{@code fused} 为权威顺序与业务 score（来自线上融合函数）。
     *
     * @param vectorRank     chunkId → 向量路 rank
     * @param keywordRank    chunkId → 普通路 rank
     * @param contributions  chunkId → [向量贡献, 普通贡献]
     */
    public static List<RetrievalTrace.FusedHit> fuseHits(List<RetrievalResult.Hit> fused,
                                                         Map<String, Integer> vectorRank,
                                                         Map<String, Integer> keywordRank,
                                                         Map<String, double[]> contributions,
                                                         double minReliableScore) {
        if (fused == null || fused.isEmpty()) {
            return List.of();
        }
        List<RetrievalTrace.FusedHit> out = new ArrayList<>(fused.size());
        int rank = 1;
        for (RetrievalResult.Hit hit : fused) {
            if (hit == null || hit.chunkId() == null || hit.chunkId().isBlank()) {
                continue;
            }
            Double rrf = numberOf(hit.metadata(), "rrf_score");
            Double normalized = numberOf(hit.metadata(), "rrf_normalized");
            double[] contrib = contributions == null ? null : contributions.get(hit.chunkId());
            out.add(new RetrievalTrace.FusedHit(
                    rank++,
                    hit.chunkId(),
                    stringOf(metaOf(hit, "doc_id")),
                    longOf(metaOf(hit, "version_id")),
                    stringOf(metaOf(hit, "version")),
                    stringOf(metaOf(hit, "section")),
                    intOf(metaOf(hit, "revision")),
                    stringOf(metaOf(hit, "content_hash")),
                    hit.content(),
                    vectorRank == null ? null : vectorRank.get(hit.chunkId()),
                    keywordRank == null ? null : keywordRank.get(hit.chunkId()),
                    contrib == null || contrib.length < 2 ? null : contrib[0],
                    contrib == null || contrib.length < 2 ? null : contrib[1],
                    rrf == null ? hit.score() : rrf,
                    normalized == null ? hit.score() : normalized,
                    hit.score(),
                    hit.score() >= minReliableScore,
                    hit.metadata() == null ? Map.of() : hit.metadata()));
        }
        return List.copyOf(out);
    }

    /**
     * 按 chunkId 去重，保留先出现的命中（与线上融合的 dedupe 一致）。
     */
    public static List<RetrievalResult.Hit> dedupe(List<RetrievalResult.Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<String, RetrievalResult.Hit> byId = new LinkedHashMap<>();
        for (RetrievalResult.Hit hit : hits) {
            if (hit == null || hit.chunkId() == null || hit.chunkId().isBlank()) {
                continue;
            }
            byId.putIfAbsent(hit.chunkId(), hit);
        }
        return List.copyOf(byId.values());
    }

    public static Object metaOf(RetrievalResult.Hit hit, String key) {
        if (hit == null || hit.metadata() == null) {
            return null;
        }
        return hit.metadata().get(key);
    }

    private static Double numberOf(Map<String, Object> meta, String key) {
        Object value = meta == null ? null : meta.get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
