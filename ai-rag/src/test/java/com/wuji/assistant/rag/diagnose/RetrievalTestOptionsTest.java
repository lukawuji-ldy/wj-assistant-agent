package com.wuji.assistant.rag.diagnose;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 检索测试临时参数：范围校验与默认回退。
 */
class RetrievalTestOptionsTest {

    @Test
    void defaults_areNullSoCallerFallbackApplies() {
        RetrievalTestOptions options = RetrievalTestOptions.defaults();
        assertEquals(7, options.vectorTopKOrDefault(7));
        assertEquals(7, options.keywordTopKOrDefault(7));
        assertEquals(7, options.finalTopKOrDefault(7));
        assertEquals(0.5d, options.minReliableScoreOrDefault(0.5d), 1e-12);
        assertEquals(99, options.rrfRankConstantOrDefault(99));
    }

    @Test
    void defaultConstants_matchDesign() {
        assertEquals(20, RetrievalTestOptions.DEFAULT_VECTOR_TOPK);
        assertEquals(20, RetrievalTestOptions.DEFAULT_KEYWORD_TOPK);
        assertEquals(10, RetrievalTestOptions.DEFAULT_FINAL_TOPK);
        assertEquals(0.0d, RetrievalTestOptions.DEFAULT_MIN_RELIABLE_SCORE, 1e-12);
        assertEquals(60, RetrievalTestOptions.DEFAULT_RRF_RANK_CONSTANT);
    }

    @Test
    void explicitValuesOverrideFallback() {
        RetrievalTestOptions options = new RetrievalTestOptions(30, 40, 5, 0.72d, 100);
        assertEquals(30, options.vectorTopKOrDefault(1));
        assertEquals(40, options.keywordTopKOrDefault(1));
        assertEquals(5, options.finalTopKOrDefault(1));
        assertEquals(0.72d, options.minReliableScoreOrDefault(0.0d), 1e-12);
        assertEquals(100, options.rrfRankConstantOrDefault(1));
    }

    @Test
    void validate_acceptsBoundaryValues() {
        new RetrievalTestOptions(1, 100, 50, 0.0d, 1).validate();
        new RetrievalTestOptions(100, 1, 1, 1.0d, 1000).validate();
    }

    @Test
    void validate_rejectsOutOfRange() {
        assertBadRequest(new RetrievalTestOptions(0, null, null, null, null));
        assertBadRequest(new RetrievalTestOptions(101, null, null, null, null));
        assertBadRequest(new RetrievalTestOptions(null, 0, null, null, null));
        assertBadRequest(new RetrievalTestOptions(null, 101, null, null, null));
        assertBadRequest(new RetrievalTestOptions(null, null, 0, null, null));
        assertBadRequest(new RetrievalTestOptions(null, null, 51, null, null));
        assertBadRequest(new RetrievalTestOptions(null, null, null, -0.01d, null));
        assertBadRequest(new RetrievalTestOptions(null, null, null, 1.01d, null));
        assertBadRequest(new RetrievalTestOptions(null, null, null, null, 0));
        assertBadRequest(new RetrievalTestOptions(null, null, null, null, 1001));
    }

    private static void assertBadRequest(RetrievalTestOptions options) {
        WujiException ex = assertThrows(WujiException.class, options::validate);
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}
