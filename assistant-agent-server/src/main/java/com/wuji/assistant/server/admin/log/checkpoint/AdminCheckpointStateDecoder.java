package com.wuji.assistant.server.admin.log.checkpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.alibaba.cloud.ai.graph.StateGraph;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 还原 PostgresSaver {@code state_data.binaryPayload}（Base64 + StateSerializer）。
 *
 * @author liudy
 */
@Component
public class AdminCheckpointStateDecoder {

    private final ObjectMapper objectMapper;

    public AdminCheckpointStateDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解码结果。
     *
     * @param decodedState  可读 JSON 树（失败为 null）
     * @param stateEntries  顶层 key 表格行
     * @param messages      messages 表格行
     * @param decodeError   失败原因
     */
    public record DecodedState(
            JsonNode decodedState,
            List<AdminCheckpointStateEntry> stateEntries,
            List<AdminCheckpointMessageView> messages,
            String decodeError
    ) {
        static DecodedState empty(String error) {
            return new DecodedState(null, List.of(), List.of(), error);
        }
    }

    /**
     * 从库内 state_data JSON 还原。
     */
    public DecodedState decode(JsonNode stateData) {
        if (stateData == null || stateData.isNull()) {
            return DecodedState.empty("state_data 为空");
        }
        JsonNode payloadNode = stateData.get("binaryPayload");
        if (payloadNode == null || !payloadNode.isTextual() || !StringUtils.hasText(payloadNode.asText())) {
            // 可能已是明文 JSON
            return fromPlainMap(toPlainMap(stateData), null);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(payloadNode.asText().trim());
            Map<String, Object> map = StateGraph.DEFAULT_JACKSON_SERIALIZER.dataFromBytes(bytes);
            return fromPlainMap(map, null);
        } catch (Exception e) {
            return DecodedState.empty("解码 binaryPayload 失败: " + e.getMessage());
        }
    }

    private DecodedState fromPlainMap(Map<String, Object> map, String error) {
        if (map == null) {
            return DecodedState.empty(error != null ? error : "解码结果为空");
        }
        List<AdminCheckpointMessageView> messages = new ArrayList<>();
        List<AdminCheckpointStateEntry> entries = new ArrayList<>();
        ObjectNode decoded = objectMapper.createObjectNode();

        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if ("messages".equals(key) && value instanceof List<?> list) {
                ArrayNode arr = objectMapper.createArrayNode();
                int i = 0;
                for (Object item : list) {
                    AdminCheckpointMessageView view = toMessageView(i++, item);
                    messages.add(view);
                    arr.add(objectMapper.valueToTree(view));
                }
                decoded.set("messages", arr);
                entries.add(new AdminCheckpointStateEntry(
                        "messages", "list", messages.size() + " 条消息", null));
            } else {
                JsonNode node = toJsonNode(value);
                decoded.set(key, node);
                entries.add(new AdminCheckpointStateEntry(
                        key,
                        typeOf(value),
                        summarize(node),
                        node));
            }
        }
        return new DecodedState(decoded, entries, messages, error);
    }

    private Map<String, Object> toPlainMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, Map.class);
    }

    private AdminCheckpointMessageView toMessageView(int index, Object item) {
        if (item instanceof UserMessage m) {
            return new AdminCheckpointMessageView(index, "USER", m.getText(), null, null);
        }
        if (item instanceof SystemMessage m) {
            return new AdminCheckpointMessageView(index, "SYSTEM", m.getText(), null, null);
        }
        if (item instanceof AssistantMessage m) {
            String tools = null;
            try {
                var toolCalls = m.getToolCalls();
                if (toolCalls != null && !toolCalls.isEmpty()) {
                    List<Map<String, Object>> calls = new ArrayList<>();
                    for (var tc : toolCalls) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", tc.id());
                        row.put("name", tc.name());
                        row.put("type", tc.type());
                        row.put("arguments", tc.arguments());
                        calls.add(row);
                    }
                    tools = writeJson(calls);
                }
            } catch (Exception ignored) {
                tools = String.valueOf(m.getToolCalls());
            }
            return new AdminCheckpointMessageView(index, "ASSISTANT", m.getText(), tools, null);
        }
        if (item instanceof ToolResponseMessage m) {
            String responses = null;
            try {
                var respList = m.getResponses();
                if (respList != null && !respList.isEmpty()) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (var r : respList) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", r.id());
                        row.put("name", r.name());
                        row.put("responseData", r.responseData());
                        rows.add(row);
                    }
                    responses = writeJson(rows);
                }
            } catch (Exception ignored) {
                responses = String.valueOf(m.getResponses());
            }
            return new AdminCheckpointMessageView(index, "TOOL", m.getText(), null, responses);
        }
        if (item instanceof Message m) {
            return new AdminCheckpointMessageView(
                    index,
                    m.getMessageType() == null ? "MESSAGE" : m.getMessageType().name(),
                    m.getText(),
                    null,
                    null);
        }
        if (item instanceof Map<?, ?> map) {
            Object roleObj = map.get("messageType");
            if (roleObj == null) {
                roleObj = map.get("role");
            }
            if (roleObj == null) {
                roleObj = map.get("@class");
            }
            String role = roleObj == null ? "UNKNOWN" : String.valueOf(roleObj);
            Object text = map.get("text");
            if (text == null) {
                text = map.get("content");
            }
            return new AdminCheckpointMessageView(
                    index,
                    simplifyRole(role),
                    text == null ? null : String.valueOf(text),
                    writeJson(map.get("toolCalls")),
                    writeJson(map.get("responses")));
        }
        return new AdminCheckpointMessageView(index, "UNKNOWN", String.valueOf(item), null, null);
    }

    private static String simplifyRole(String raw) {
        if (raw == null) {
            return "UNKNOWN";
        }
        String s = raw;
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        if (s.contains("User")) {
            return "USER";
        }
        if (s.contains("Assistant")) {
            return "ASSISTANT";
        }
        if (s.contains("System")) {
            return "SYSTEM";
        }
        if (s.contains("Tool")) {
            return "TOOL";
        }
        return s.toUpperCase();
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.valueToTree(sanitize(value));
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(String.valueOf(value));
        }
    }

    private Object sanitize(Object value) {
        if (value instanceof Message) {
            return toMessageView(0, value);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(sanitize(o));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), sanitize(e.getValue()));
            }
            return out;
        }
        return value;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sanitize(value));
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String typeOf(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            return "object";
        }
        if (value instanceof List) {
            return "list";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return "string";
    }

    private static String summarize(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isTextual()) {
            String t = node.asText();
            return t.length() > 120 ? t.substring(0, 120) + "…" : t;
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            return "array[" + node.size() + "]";
        }
        if (node.isObject()) {
            return "object{" + node.size() + " keys}";
        }
        return node.toString();
    }
}
