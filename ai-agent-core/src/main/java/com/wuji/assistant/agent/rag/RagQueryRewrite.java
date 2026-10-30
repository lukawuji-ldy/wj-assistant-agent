package com.wuji.assistant.agent.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询改写结果。{@code rewritten=false} 表示沿用原问题。
 *
 * @param queries     实际用于检索的问句，原问题在前
 * @param relational  是否关系/对比类问法
 * @param rewritten   是否成功采用了模型输出
 * @author liudy
 */
public record RagQueryRewrite(List<String> queries, boolean relational, boolean rewritten) {

    public static RagQueryRewrite original(String query) {
        if (!StringUtils.hasText(query)) {
            return new RagQueryRewrite(List.of(), false, false);
        }
        return new RagQueryRewrite(List.of(query.trim()), false, false);
    }

    /**
     * 解析模型 JSON。失败、空列表都退回原问题。
     *
     * @param raw        模型原文
     * @param original   用户原问题
     * @param maxQueries 含原问题在内的上限
     * @param mapper     Jackson
     * @return 改写结果；解析失败时 rewritten=false
     */
    public static RagQueryRewrite parse(String raw, String original, int maxQueries, ObjectMapper mapper) {
        RagQueryRewrite fallback = original(original);
        if (!StringUtils.hasText(raw) || mapper == null) {
            return fallback;
        }
        int cap = Math.max(1, maxQueries);
        try {
            JsonNode root = mapper.readTree(unwrapJson(raw));
            if (root == null || !root.isObject()) {
                return fallback;
            }
            List<String> queries = new ArrayList<>();
            if (StringUtils.hasText(original)) {
                queries.add(original.trim());
            }
            JsonNode items = root.get("queries");
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    if (item == null || item.isNull()) {
                        continue;
                    }
                    String text = item.asText("").trim();
                    if (!StringUtils.hasText(text) || queries.contains(text)) {
                        continue;
                    }
                    queries.add(text);
                    if (queries.size() >= cap) {
                        break;
                    }
                }
            }
            if (queries.isEmpty()) {
                return fallback;
            }
            boolean relational = root.path("relational").asBoolean(false);
            return new RagQueryRewrite(List.copyOf(queries), relational, true);
        } catch (Exception e) {
            return fallback;
        }
    }

    static String unwrapJson(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNl = text.indexOf('\n');
            if (firstNl > 0) {
                text = text.substring(firstNl + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
        }
        return text.trim();
    }
}
