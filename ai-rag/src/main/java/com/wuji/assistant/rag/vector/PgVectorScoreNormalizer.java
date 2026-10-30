package com.wuji.assistant.rag.vector;

import com.wuji.assistant.rag.RetrievalResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PG 余弦与关键词两路的排名融合。
 * <p>
 * 顺序由 {@link ClientRrfFusion} 决定。对外 score 在余弦路出现过时保留余弦相似度，
 * 避免关键词路把同一 chunk 盖成 1.0；只出现在关键词路时用 {@code 1 - rank / total}（rank 从 0 起）。
 *
 * @author liudy
 */
public final class PgVectorScoreNormalizer {

    public static final int DEFAULT_RANK_CONSTANT = 60;

    private PgVectorScoreNormalizer() {
    }

    /**
     * @param zeroBasedRank 该路中的名次，0 为最靠前
     * @param total         该路有效条数
     * @return 关键词排名分；首条为 1，其后递减
     */
    public static double keywordRankScore(int zeroBasedRank, int total) {
        if (total <= 0 || zeroBasedRank < 0 || zeroBasedRank >= total) {
            return 0;
        }
        return 1.0d - (zeroBasedRank / (double) total);
    }

    /**
     * @param cosineHits   余弦路，列表顺序即排名
     * @param keywordHits  关键词路，列表顺序即排名；入参 score 忽略
     * @param limit        融合后条数
     * @param rankConstant RRF rank_constant
     * @return 按 RRF 排序的命中
     */
    public static List<RetrievalResult.Hit> fuse(List<RetrievalResult.Hit> cosineHits,
                                                 List<RetrievalResult.Hit> keywordHits,
                                                 int limit,
                                                 int rankConstant) {
        List<RetrievalResult.Hit> cosine = dedupe(cosineHits);
        List<RetrievalResult.Hit> keyword = scoreKeywords(dedupe(keywordHits));
        return fuseRankedLists(List.of(cosine, keyword), limit, rankConstant);
    }

    /**
     * 多路已经排好序的命中（每个内层列表是一路）。同一 chunkId 保留先出现的正文与 score。
     *
     * @param rankedLists  各路结果
     * @param limit        融合后条数
     * @param rankConstant RRF rank_constant
     * @return 按 RRF 排序的命中
     */
    public static List<RetrievalResult.Hit> fuseRankedLists(List<List<RetrievalResult.Hit>> rankedLists,
                                                            int limit,
                                                            int rankConstant) {
        if (rankedLists == null || rankedLists.isEmpty() || limit <= 0) {
            return List.of();
        }
        int constant = rankConstant < 1 ? DEFAULT_RANK_CONSTANT : rankConstant;
        Map<String, RetrievalResult.Hit> byId = new LinkedHashMap<>();
        List<List<ClientRrfFusion.RankedDoc>> ranked = new ArrayList<>();
        for (List<RetrievalResult.Hit> group : rankedLists) {
            List<ClientRrfFusion.RankedDoc> docs = new ArrayList<>();
            if (group != null) {
                for (RetrievalResult.Hit hit : group) {
                    if (!usable(hit)) {
                        continue;
                    }
                    docs.add(new ClientRrfFusion.RankedDoc(hit.chunkId(), contentHash(hit)));
                    byId.putIfAbsent(hit.chunkId(), hit);
                }
            }
            ranked.add(docs);
        }
        List<ClientRrfFusion.FusedDoc> fused = ClientRrfFusion.fuse(ranked, constant, Math.max(1, limit));
        List<RetrievalResult.Hit> out = new ArrayList<>(fused.size());
        for (ClientRrfFusion.FusedDoc doc : fused) {
            RetrievalResult.Hit base = byId.get(doc.id());
            if (base == null) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            if (base.metadata() != null) {
                meta.putAll(base.metadata());
            }
            meta.put("rrf_score", doc.rrfScore());
            meta.put("rrf_normalized", doc.normalizedScore());
            out.add(new RetrievalResult.Hit(base.chunkId(), base.content(), base.score(), Map.copyOf(meta)));
        }
        return out;
    }

    private static List<RetrievalResult.Hit> scoreKeywords(List<RetrievalResult.Hit> keyword) {
        int total = keyword.size();
        List<RetrievalResult.Hit> scored = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            RetrievalResult.Hit hit = keyword.get(i);
            scored.add(new RetrievalResult.Hit(
                    hit.chunkId(), hit.content(), keywordRankScore(i, total), hit.metadata()));
        }
        return scored;
    }

    private static List<RetrievalResult.Hit> dedupe(List<RetrievalResult.Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<String, RetrievalResult.Hit> byId = new LinkedHashMap<>();
        for (RetrievalResult.Hit hit : hits) {
            if (!usable(hit)) {
                continue;
            }
            byId.putIfAbsent(hit.chunkId(), hit);
        }
        return List.copyOf(byId.values());
    }

    private static boolean usable(RetrievalResult.Hit hit) {
        return hit != null && hit.chunkId() != null && !hit.chunkId().isBlank();
    }

    private static String contentHash(RetrievalResult.Hit hit) {
        if (hit.metadata() == null) {
            return null;
        }
        Object value = hit.metadata().get("content_hash");
        return value == null ? null : String.valueOf(value);
    }
}
