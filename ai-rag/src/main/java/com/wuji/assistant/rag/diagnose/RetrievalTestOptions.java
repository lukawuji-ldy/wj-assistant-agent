package com.wuji.assistant.rag.diagnose;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;

/**
 * 检索测试临时参数覆盖。仅本次请求生效，不修改任何单例配置。
 * <p>
 * 字段为 {@code null} 表示使用默认值。取值范围：
 * {@code vectorTopK}/{@code keywordTopK} 1..100、{@code finalTopK} 1..50、
 * {@code minReliableScore} 0..1、{@code rrfRankConstant} 1..1000。
 *
 * @author liudy
 */
public record RetrievalTestOptions(
        Integer vectorTopK,
        Integer keywordTopK,
        Integer finalTopK,
        Double minReliableScore,
        Integer rrfRankConstant
) {

    public static final int DEFAULT_VECTOR_TOPK = 20;
    public static final int DEFAULT_KEYWORD_TOPK = 20;
    public static final int DEFAULT_FINAL_TOPK = 10;
    public static final double DEFAULT_MIN_RELIABLE_SCORE = 0.0d;
    public static final int DEFAULT_RRF_RANK_CONSTANT = 60;

    public static RetrievalTestOptions defaults() {
        return new RetrievalTestOptions(null, null, null, null, null);
    }

    /**
     * 校验取值范围。越界抛 {@link ErrorCode#BAD_REQUEST}。
     */
    public RetrievalTestOptions validate() {
        checkRange(vectorTopK, 1, 100, "vectorTopK");
        checkRange(keywordTopK, 1, 100, "keywordTopK");
        checkRange(finalTopK, 1, 50, "finalTopK");
        if (minReliableScore != null && (minReliableScore < 0d || minReliableScore > 1d)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "minReliableScore 必须在 0..1");
        }
        checkRange(rrfRankConstant, 1, 1000, "rrfRankConstant");
        return this;
    }

    public int vectorTopKOrDefault(int fallback) {
        return vectorTopK == null ? fallback : vectorTopK;
    }

    public int keywordTopKOrDefault(int fallback) {
        return keywordTopK == null ? fallback : keywordTopK;
    }

    public int finalTopKOrDefault(int fallback) {
        return finalTopK == null ? fallback : finalTopK;
    }

    public double minReliableScoreOrDefault(double fallback) {
        return minReliableScore == null ? fallback : minReliableScore;
    }

    public int rrfRankConstantOrDefault(int fallback) {
        return rrfRankConstant == null ? fallback : rrfRankConstant;
    }

    private static void checkRange(Integer value, int min, int max, String name) {
        if (value == null) {
            return;
        }
        if (value < min || value > max) {
            throw new WujiException(ErrorCode.BAD_REQUEST, name + " 必须在 " + min + ".." + max);
        }
    }
}
