package com.wuji.assistant.rag.ingest;

/**
 * 章节标题写入 chunk.section 的方式。
 *
 * @author liudy
 */
public enum SectionTitleMode {
    /** 仅正则匹配到的前缀（如「一、」）。 */
    MATCH,
    /** 匹配行整行（推荐）。 */
    FULL_LINE;

    /**
     * 解析枚举；非法或空 → null。
     */
    public static SectionTitleMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SectionTitleMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
