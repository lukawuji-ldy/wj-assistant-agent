package com.wuji.assistant.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

/**
 * 解析 llm_config.extra_json 约定键。
 *
 * @author liudy
 */
final class LlmExtraJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmExtraJson() {
    }

    static String text(String extraJson, String key) {
        if (!StringUtils.hasText(extraJson) || !StringUtils.hasText(key)) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(extraJson);
            JsonNode v = node.get(key);
            if (v == null || v.isNull()) {
                return null;
            }
            String s = v.asText();
            return StringUtils.hasText(s) ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    static Integer integer(String extraJson, String key) {
        if (!StringUtils.hasText(extraJson) || !StringUtils.hasText(key)) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(extraJson);
            JsonNode v = node.get(key);
            if (v == null || v.isNull() || !v.isNumber()) {
                return null;
            }
            return v.asInt();
        } catch (Exception e) {
            return null;
        }
    }
}
