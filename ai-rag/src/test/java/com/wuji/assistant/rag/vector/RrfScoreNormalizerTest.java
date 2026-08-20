package com.wuji.assistant.rag.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RRF 分数归一化单测。
 */
class RrfScoreNormalizerTest {

    @Test
    void normalizesWithDefaultRankConstant() {
        double normalized = RrfScoreNormalizer.normalize(0.5, 60);
        assertEquals(0.5 * 61.0 / 2.0, normalized, 1e-9);
    }

    @Test
    void zeroRrfScoreYieldsZero() {
        assertEquals(0.0, RrfScoreNormalizer.normalize(0.0, 60));
    }
}
