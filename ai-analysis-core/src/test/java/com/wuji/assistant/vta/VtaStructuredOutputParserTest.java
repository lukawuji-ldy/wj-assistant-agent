package com.wuji.assistant.vta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VtaStructuredOutputParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseCustomerTagWithPrefixSuffix() {
        VtaStructuredOutputParser parser = new VtaStructuredOutputParser(objectMapper);
        String raw = "思考后输出：```json\n{\"客户标签\":\"a,b\",\"标签命中原因\":\"ok\"}\n```";
        JsonNode node = parser.parseCustomerTag(raw);
        assertTrue(node.hasNonNull("客户标签"));
        assertEquals("a,b", node.path("客户标签").asText());
        assertEquals("ok", node.path("标签命中原因").asText());
    }

    @Test
    void missingFieldsReturnsErrorNode() {
        VtaStructuredOutputParser parser = new VtaStructuredOutputParser(objectMapper);
        JsonNode node = parser.parseCallSummary("{\"foo\":\"bar\"}");
        assertTrue(node.hasNonNull("error"));
        assertEquals(VtaStructuredOutputParser.PARSE_ERROR_CODE, node.path("error").asText());
    }
}

