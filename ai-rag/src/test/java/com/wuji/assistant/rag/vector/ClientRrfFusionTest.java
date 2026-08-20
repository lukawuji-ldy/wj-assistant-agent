package com.wuji.assistant.rag.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端 RRF 融合：对齐 ES 原生 {@code score = Σ 1/(rankConstant + rank)}（1-based rank）。
 */
class ClientRrfFusionTest {

    @Test
    void bothListsRankOne_yieldsMaxNormalizedScore() {
        List<ClientRrfFusion.RankedDoc> bm25 = List.of(
                new ClientRrfFusion.RankedDoc("a", "h1"),
                new ClientRrfFusion.RankedDoc("b", "h2"));
        List<ClientRrfFusion.RankedDoc> knn = List.of(
                new ClientRrfFusion.RankedDoc("a", "h1"),
                new ClientRrfFusion.RankedDoc("c", "h3"));

        List<ClientRrfFusion.FusedDoc> fused = ClientRrfFusion.fuse(List.of(bm25, knn), 60, 10);

        assertEquals("a", fused.get(0).id());
        double expectedRaw = 1.0 / 61.0 + 1.0 / 61.0;
        assertEquals(expectedRaw, fused.get(0).rrfScore(), 1e-12);
        assertEquals(RrfScoreNormalizer.normalize(expectedRaw, 60), fused.get(0).normalizedScore(), 1e-12);
        assertEquals(1.0, fused.get(0).normalizedScore(), 1e-12);
    }

    @Test
    void singleListOnly_usesOneContribution() {
        List<ClientRrfFusion.RankedDoc> bm25 = List.of(
                new ClientRrfFusion.RankedDoc("only", "h"));

        List<ClientRrfFusion.FusedDoc> fused = ClientRrfFusion.fuse(List.of(bm25, List.of()), 60, 5);

        assertEquals(1, fused.size());
        assertEquals(1.0 / 61.0, fused.get(0).rrfScore(), 1e-12);
        assertEquals(0.5, fused.get(0).normalizedScore(), 1e-12);
    }

    @Test
    void respectsTopKAndStableOrderByScore() {
        List<ClientRrfFusion.RankedDoc> bm25 = List.of(
                new ClientRrfFusion.RankedDoc("b", null),
                new ClientRrfFusion.RankedDoc("a", null),
                new ClientRrfFusion.RankedDoc("c", null));
        List<ClientRrfFusion.RankedDoc> knn = List.of(
                new ClientRrfFusion.RankedDoc("a", "ha"),
                new ClientRrfFusion.RankedDoc("b", "hb"));

        List<ClientRrfFusion.FusedDoc> fused = ClientRrfFusion.fuse(List.of(bm25, knn), 60, 2);

        assertEquals(2, fused.size());
        // a: bm25 rank2 + knn rank1; b: bm25 rank1 + knn rank2 → equal RRF
        double scoreA = 1.0 / 62.0 + 1.0 / 61.0;
        double scoreB = 1.0 / 61.0 + 1.0 / 62.0;
        assertEquals(scoreA, scoreB, 1e-12);
        assertTrue(fused.get(0).rrfScore() >= fused.get(1).rrfScore());
        assertEquals("ha", fused.stream().filter(d -> d.id().equals("a")).findFirst().orElseThrow().contentHash());
    }

    @Test
    void emptyInputs_returnEmpty() {
        assertTrue(ClientRrfFusion.fuse(List.of(), 60, 5).isEmpty());
        assertTrue(ClientRrfFusion.fuse(List.of(List.of(), List.of()), 60, 5).isEmpty());
    }
}
