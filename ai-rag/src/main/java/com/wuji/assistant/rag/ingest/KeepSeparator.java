package com.wuji.assistant.rag.ingest;

/**
 * 递归切分时分隔符保留策略。
 *
 * @author liudy
 */
public enum KeepSeparator {
    /** 分隔符留在当前块末尾（默认，与历史行为一致）。 */
    APPEND,
    /** 分隔符放到下一块开头。 */
    PREPEND,
    /** 丢弃分隔符。 */
    DROP;

    /**
     * 解析枚举；非法或空 → null。
     */
    public static KeepSeparator parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return KeepSeparator.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
