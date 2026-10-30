package com.wuji.assistant.rag.vector;

import com.wuji.assistant.rag.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 余弦与关键词重叠时，排名走 RRF，分数不被关键词 1.0 覆盖。
 *
 * @author liudy
 */
class PgVectorScoreNormalizerTest {

    @Test
    void overlapKeepsCosineScoreAndRanksSharedChunkFirst() {
        RetrievalResult.Hit cosineA = new RetrievalResult.Hit("a", "仅余弦", 0.91, Map.of());
        RetrievalResult.Hit cosineB = new RetrievalResult.Hit("b", "重叠", 0.55, Map.of());
        RetrievalResult.Hit keywordB = new RetrievalResult.Hit("b", "重叠关键词", 1.0, Map.of());
        RetrievalResult.Hit keywordC = new RetrievalResult.Hit("c", "关键词二", 1.0, Map.of());
        RetrievalResult.Hit keywordD = new RetrievalResult.Hit("d", "关键词三", 1.0, Map.of());

        List<RetrievalResult.Hit> fused = PgVectorScoreNormalizer.fuse(
                List.of(cosineA, cosineB),
                List.of(keywordB, keywordC, keywordD),
                5,
                60);

        assertEquals(List.of("b", "a", "c", "d"), fused.stream().map(RetrievalResult.Hit::chunkId).toList());
        assertEquals(0.55, fused.get(0).score(), 1e-6);
        assertEquals(0.91, fused.get(1).score(), 1e-6);
        assertEquals(1.0 - 1.0 / 3.0, fused.get(2).score(), 1e-6);
        assertEquals(1.0 - 2.0 / 3.0, fused.get(3).score(), 1e-6);
        assertTrue(fused.stream().noneMatch(hit -> hit.chunkId().equals("b") && hit.score() == 1.0));
    }

    @Test
    void keywordRanksAreNotAllPerfect() {
        List<RetrievalResult.Hit> fused = PgVectorScoreNormalizer.fuse(
                List.of(),
                List.of(
                        new RetrievalResult.Hit("k1", "一", 1.0, Map.of()),
                        new RetrievalResult.Hit("k2", "二", 1.0, Map.of())),
                5,
                60);
        assertEquals(1.0, fused.get(0).score(), 1e-6);
        assertEquals(0.5, fused.get(1).score(), 1e-6);
    }
}
