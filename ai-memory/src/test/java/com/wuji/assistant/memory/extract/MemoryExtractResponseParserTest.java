package com.wuji.assistant.memory.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 解析与落库闸门单测。
 *
 * @author liudy
 */
class MemoryExtractResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parse_multiActions_andFence() {
        String raw = """
                ```json
                {"actions":[
                  {"action":"UPDATE","type":"PROFILE","key":"hometown","newValue":"山西大同","confidence":0.9},
                  {"action":"UPDATE","type":"PROFILE","key":"residence","newValue":"河北燕郊","confidence":0.88},
                  {"action":"INSERT","type":"SEMANTIC","content":"用户在河北燕郊居住但籍贯山西大同","confidence":0.7,"importance":0.6}
                ]}
                ```
                """;
        List<MemoryActionItem> list = MemoryExtractResponseParser.parse(raw, mapper);
        assertEquals(3, list.size());
        assertEquals("hometown", list.get(0).memoryKey());
        assertEquals("山西大同", list.get(0).memoryValue());
        assertEquals("SEMANTIC", list.get(2).resultType());
        assertEquals("用户在河北燕郊居住但籍贯山西大同", list.get(2).content());
    }

    @Test
    void gate_rejectsInvalidProfileAndSemanticWithoutContent() {
        assertFalse(MemoryActionGate.accept(
                new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", null, "x")));
        assertFalse(MemoryActionGate.accept(
                new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", "k", "")));
        assertFalse(MemoryActionGate.accept(
                new MemoryActionItem(MemoryAction.INSERT, "SEMANTIC", null, null, null, 0.9, 0.5, null, null)));
        assertTrue(MemoryActionGate.accept(
                new MemoryActionItem(MemoryAction.INSERT, "SEMANTIC", null, null, "有内容", 0.9, 0.5, null, null)));
        assertTrue(MemoryActionGate.belowMinConfidence(
                new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", "k", "v", null, 0.2, null, null, null),
                0.55));
    }

    @Test
    void parse_invalidThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MemoryExtractResponseParser.parse("not-json", mapper));
    }
}
