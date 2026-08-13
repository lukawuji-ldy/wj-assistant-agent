package com.wuji.assistant.vta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 结构化输出解析（严格字段校验 + 失败可降级）。
 *
 * 解析失败不抛出异常，改为返回形如：
 * {@code {"error":"VTA_JSON_PARSE_FAILED","raw":"<model_output>"}} 的 JSON 对象。
 */
public class VtaStructuredOutputParser {

    public static final String PARSE_ERROR_CODE = "VTA_JSON_PARSE_FAILED";

    private final ObjectMapper objectMapper;

    public VtaStructuredOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parseCustomerTag(String raw) {
        JsonNode obj = parseJsonObject(raw);
        if (obj == null || !obj.hasNonNull("客户标签") || !obj.hasNonNull("标签命中原因")) {
            return errorNode(raw, "missing_fields");
        }
        return obj;
    }

    public JsonNode parseSalesTag(String raw) {
        JsonNode obj = parseJsonObject(raw);
        if (obj == null || !obj.hasNonNull("销售标签") || !obj.hasNonNull("标签命中原因")) {
            return errorNode(raw, "missing_fields");
        }
        return obj;
    }

    public JsonNode parseCallSummary(String raw) {
        JsonNode obj = parseJsonObject(raw);
        if (obj == null || !obj.hasNonNull("总结文本")) {
            return errorNode(raw, "missing_fields");
        }
        return obj;
    }

    public JsonNode parseIntentScore(String raw) {
        JsonNode obj = parseJsonObject(raw);
        if (obj == null || !obj.hasNonNull("意向度") || !obj.hasNonNull("意向度判断依据")) {
            return errorNode(raw, "missing_fields");
        }
        return obj;
    }

    /**
     * 解析 aggregate 输出。
     */
    public JsonNode parseAggregate(String raw) {
        JsonNode obj = parseJsonObject(raw);
        if (obj == null || !obj.hasNonNull("aggregateText") || !obj.hasNonNull("raw")) {
            return errorNode(raw, "missing_fields");
        }
        return obj;
    }

    private JsonNode parseJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // 模型偶发输出 markdown/前后缀：尝试抽取首个 { ... }。
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String candidate = s.substring(start, end + 1);
        try {
            JsonNode node = objectMapper.readTree(candidate);
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ObjectNode errorNode(String raw, String detail) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("error", PARSE_ERROR_CODE);
        out.put("detail", detail);
        out.put("raw", raw == null ? "" : raw);
        return out;
    }
}

