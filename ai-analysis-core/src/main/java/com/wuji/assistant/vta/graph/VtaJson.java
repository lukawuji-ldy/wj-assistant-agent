package com.wuji.assistant.vta.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.assistant.vta.VtaStructuredOutputParser;

/**
 * OverAllState 中 JSON 取值 / 错误节点。
 */
public final class VtaJson {

    private VtaJson() {
    }

    public static JsonNode asJson(ObjectMapper mapper, Object value) {
        if (value == null) {
            return mapper.createObjectNode();
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        return mapper.valueToTree(value);
    }

    public static boolean hasError(JsonNode node) {
        return node != null && node.isObject() && node.hasNonNull("error");
    }

    public static ObjectNode errorNode(ObjectMapper mapper, Throwable ex) {
        return errorNode(mapper, VtaStructuredOutputParser.PARSE_ERROR_CODE,
                ex == null || ex.getMessage() == null ? "" : ex.getMessage());
    }

    public static ObjectNode errorNode(ObjectMapper mapper, String code, String detail) {
        ObjectNode out = mapper.createObjectNode();
        out.put("error", code == null ? VtaStructuredOutputParser.PARSE_ERROR_CODE : code);
        out.put("detail", detail == null ? "" : detail);
        out.put("raw", "");
        return out;
    }

    public static ObjectNode timeoutNode(ObjectMapper mapper, String nodeName) {
        return errorNode(mapper, "VTA_GRAPH_TIMEOUT",
                (nodeName == null ? "node" : nodeName) + " timed out");
    }

    public static ObjectNode missingPrompt(ObjectMapper mapper, String promptCode) {
        ObjectNode out = mapper.createObjectNode();
        out.put("error", VtaStructuredOutputParser.PARSE_ERROR_CODE);
        out.put("detail", "missing prompt: " + promptCode);
        out.put("raw", "");
        return out;
    }
}
