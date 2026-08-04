package com.wuji.assistant.memory.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 解析 LLM Extract JSON（支持 markdown fence / 裸 JSON）。
 *
 * @author liudy
 */
public final class MemoryExtractResponseParser {

    private MemoryExtractResponseParser() {
    }

    /**
     * @param raw    模型原文
     * @param mapper Jackson
     * @return actions；解析失败抛 IllegalArgumentException
     */
    public static List<MemoryActionItem> parse(String raw, ObjectMapper mapper) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("empty extract response");
        }
        String json = unwrapJson(raw.trim());
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode actionsNode = root.has("actions") ? root.get("actions") : root;
            if (actionsNode == null || !actionsNode.isArray()) {
                throw new IllegalArgumentException("actions array required");
            }
            List<MemoryActionItem> list = new ArrayList<>();
            for (JsonNode n : actionsNode) {
                MemoryActionItem item = toItem(n);
                if (item != null) {
                    list.add(item);
                }
            }
            return list;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid extract JSON: " + e.getMessage(), e);
        }
    }

    private static MemoryActionItem toItem(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        String actionRaw = text(n, "action");
        MemoryAction action;
        try {
            action = MemoryAction.valueOf(actionRaw == null ? "IGNORE" : actionRaw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            action = MemoryAction.IGNORE;
        }
        String type = firstNonBlank(text(n, "type"), text(n, "resultType"), "NONE");
        String key = firstNonBlank(text(n, "key"), text(n, "memoryKey"));
        String value = firstNonBlank(text(n, "newValue"), text(n, "memoryValue"), text(n, "value"));
        String content = text(n, "content");
        Double confidence = asDouble(n.get("confidence"));
        Double importance = asDouble(n.get("importance"));
        String reason = text(n, "reason");
        return new MemoryActionItem(action, type == null ? "NONE" : type.toUpperCase(Locale.ROOT),
                key, value, content, confidence, importance, reason, null);
    }

    static String unwrapJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
            s = s.trim();
        }
        int startObj = s.indexOf('{');
        int startArr = s.indexOf('[');
        int start;
        if (startObj < 0) {
            start = startArr;
        } else if (startArr < 0) {
            start = startObj;
        } else {
            start = Math.min(startObj, startArr);
        }
        if (start > 0) {
            s = s.substring(start);
        }
        return s.trim();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String t = v.asText();
        return StringUtils.hasText(t) ? t.trim() : null;
    }

    private static Double asDouble(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        try {
            return Double.parseDouble(n.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
