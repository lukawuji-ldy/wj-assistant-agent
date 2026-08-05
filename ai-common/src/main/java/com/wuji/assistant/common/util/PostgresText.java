package com.wuji.assistant.common.util;

/**
 * PostgreSQL 文本 / JSONB 入参清洗。
 * <p>
 * PG 的 text 与 jsonb 均拒绝 UTF-8 NUL（{@code \u0000}）；部分兼容模型流式 chunk
 * 会夹带该字节。落库前须剥离真实 NUL；若已序列化为 JSON，还须去掉字面量
 * {@code \u0000} 转义（否则 {@code ?::jsonb} 解析失败）。
 *
 * @author liudy
 */
public final class PostgresText {

    private PostgresText() {
    }

    /**
     * 剥离字符串中的真实 NUL 字节；{@code null} 原样返回。
     *
     * @param text 原始文本，可空
     * @return 可安全写入 PG text 的文本，或 null
     */
    public static String sanitize(String text) {
        if (text == null || text.indexOf('\u0000') < 0) {
            return text;
        }
        return text.replace("\u0000", "");
    }

    /**
     * 清洗已序列化的 JSON 字符串：去真实 NUL，并去掉 Jackson 产出的 {@code \u0000} 转义。
     * 用于写入 jsonb，或作为 text 摘要的兜底。
     *
     * @param json JSON 文本，可空
     * @return 可安全写入 PG 的 JSON 文本，或 null
     */
    public static String sanitizeJson(String json) {
        if (json == null) {
            return null;
        }
        String cleaned = sanitize(json);
        // Jackson 将 char U+0000 写成字面量 \u0000；PG jsonb 拒绝该转义
        if (cleaned.contains("\\u0000")) {
            cleaned = cleaned.replace("\\u0000", "");
        }
        return cleaned;
    }
}
