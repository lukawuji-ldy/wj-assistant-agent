package com.wuji.assistant.rag;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 关键词检索用的查询切分：整句 ILIKE 对无空格中文问句几乎永不命中。
 *
 * @author liudy
 */
public final class RetrievalQueryTerms {

    /**
     * 作分隔符的停用/虚词，使「徐凤年第一次北莽之行的收获」拆出「徐凤年」「北莽」。
     */
    private static final Pattern SPLIT = Pattern.compile(
            "[\\s\\p{Punct}]+|的|了|呢|吗|啊|吧|着|过|地|得|和|与|或|及|以及"
                    + "|什么|怎么|如何|是否|可以|相关|问题|请问|第|次|之|行|收获");

    private RetrievalQueryTerms() {
    }

    /**
     * 提取长度 ≥ 2 的检索词，保序去重。
     *
     * @param query 用户问句
     * @return 词列表，可能为空
     */
    public static List<String> terms(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : SPLIT.split(query.trim())) {
            if (part != null && part.length() >= 2) {
                out.add(part);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * LIKE 字面量，去掉通配符以免用户输入扩大匹配。
     *
     * @param raw 原始片段
     * @return 安全片段
     */
    public static String likeLiteral(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("%", "").replace("_", "").replace("\\", "");
    }
}
