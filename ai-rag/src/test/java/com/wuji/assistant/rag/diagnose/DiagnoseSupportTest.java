package com.wuji.assistant.rag.diagnose;

import com.wuji.assistant.rag.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检索诊断轨迹构造：单路命中拆分、融合汇总与去重。
 */
class DiagnoseSupportTest {

    @Test
    void toHits_assignsRankAndMapsMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("doc_id", "doc-1");
        meta.put("version_id", 42L);
        meta.put("version", "v2");
        meta.put("section", "第三章");
        meta.put("revision", 3);
        meta.put("content_hash", "hash-1");
        List<RetrievalResult.Hit> hits = List.of(
                new RetrievalResult.Hit("c1", "正文一", 0.9d, meta),
                new RetrievalResult.Hit("c2", "正文二", 0.8d, Map.of()));

        List<RetrievalTrace.RetrievalHit> out = DiagnoseSupport.toHits(hits, new double[]{0.91d, 0.82d});

        assertEquals(2, out.size());
        RetrievalTrace.RetrievalHit first = out.get(0);
        assertEquals(1, first.rank());
        assertEquals("c1", first.chunkId());
        assertEquals("doc-1", first.docId());
        assertEquals(42L, first.versionId().longValue());
        assertEquals("v2", first.version());
        assertEquals("第三章", first.section());
        assertEquals(3, first.revision().intValue());
        assertEquals("hash-1", first.contentHash());
        assertEquals("正文一", first.content());
        assertEquals(0.91d, first.rawScore(), 1e-12);

        assertEquals(2, out.get(1).rank());
        assertEquals("c2", out.get(1).chunkId());
        assertNull(out.get(1).docId());
        assertEquals(0.82d, out.get(1).rawScore(), 1e-12);
    }

    @Test
    void toHits_skipsBlankChunkIdAndFallsBackToScore() {
        List<RetrievalResult.Hit> hits = List.of(
                new RetrievalResult.Hit("c1", "正文", 0.55d, null),
                new RetrievalResult.Hit("  ", "乱入", 0.4d, null),
                new RetrievalResult.Hit(null, "乱入", 0.3d, null));

        List<RetrievalTrace.RetrievalHit> out = DiagnoseSupport.toHits(hits, null);

        assertEquals(1, out.size());
        assertEquals("c1", out.get(0).chunkId());
        assertEquals(0.55d, out.get(0).rawScore(), 1e-12);
    }

    @Test
    void fuseHits_buildsRanksContributionsAndThreshold() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("rrf_score", 2.0d / 61.0);
        meta.put("rrf_normalized", 1.0d);
        meta.put("doc_id", "doc-1");
        RetrievalResult.Hit fused = new RetrievalResult.Hit("a", "正文A", 0.9d, meta);
        RetrievalResult.Hit below = new RetrievalResult.Hit("b", "正文B", 0.2d, Map.of());

        Map<String, Integer> vectorRank = Map.of("a", 1, "b", 2);
        Map<String, Integer> keywordRank = Map.of("a", 1);
        Map<String, double[]> contributions = Map.of(
                "a", new double[]{1.0 / 61.0, 1.0 / 61.0},
                "b", new double[]{1.0 / 62.0, 0.0d});

        List<RetrievalTrace.FusedHit> out = DiagnoseSupport.fuseHits(
                List.of(fused, below), vectorRank, keywordRank, contributions, 0.5d);

        assertEquals(2, out.size());
        RetrievalTrace.FusedHit a = out.get(0);
        assertEquals(1, a.rank());
        assertEquals(1, a.vectorRank().intValue());
        assertEquals(1, a.keywordRank().intValue());
        assertEquals(1.0 / 61.0, a.vectorContribution(), 1e-12);
        assertEquals(1.0 / 61.0, a.keywordContribution(), 1e-12);
        assertEquals(2.0 / 61.0, a.rrfScore(), 1e-12);
        assertEquals(1.0d, a.normalizedRrf(), 1e-12);
        assertEquals(0.9d, a.businessScore(), 1e-12);
        assertTrue(a.passedThreshold());

        RetrievalTrace.FusedHit b = out.get(1);
        assertEquals(2, b.vectorRank().intValue());
        assertNull(b.keywordRank());
        assertEquals(0.0d, b.keywordContribution(), 1e-12);
        // meta 无 rrf_* → 回退 businessScore
        assertEquals(0.2d, b.rrfScore(), 1e-12);
        assertEquals(0.2d, b.normalizedRrf(), 1e-12);
        assertFalse(b.passedThreshold());
    }

    @Test
    void dedupe_keepsFirstOccurrence() {
        RetrievalResult.Hit first = new RetrievalResult.Hit("a", "一", 0.9d, Map.of());
        RetrievalResult.Hit dup = new RetrievalResult.Hit("a", "二", 0.5d, Map.of());
        RetrievalResult.Hit other = new RetrievalResult.Hit("b", "三", 0.4d, Map.of());

        List<RetrievalResult.Hit> out = DiagnoseSupport.dedupe(List.of(first, dup, other));

        assertEquals(2, out.size());
        assertEquals("一", out.get(0).content());
        assertEquals("b", out.get(1).chunkId());
        assertTrue(DiagnoseSupport.dedupe(null).isEmpty());
    }
}
