package com.wuji.assistant.server.admin.log.checkpoint;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AdminCheckpointStateDecoder 单元测试。
 *
 * @author liudy
 */
class AdminCheckpointStateDecoderTest {

    private AdminCheckpointStateDecoder decoder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        decoder = new AdminCheckpointStateDecoder(objectMapper);
    }

    @Test
    void decodeBinaryPayloadRoundTrip() throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("messages", List.of(new UserMessage("你好")));
        state.put("jump_to", "next");
        byte[] bytes = StateGraph.DEFAULT_JACKSON_SERIALIZER.dataToBytes(state);
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("binaryPayload", Base64.getEncoder().encodeToString(bytes));

        AdminCheckpointStateDecoder.DecodedState decoded = decoder.decode(envelope);
        assertNull(decoded.decodeError());
        assertNotNull(decoded.decodedState());
        assertEquals(1, decoded.messages().size());
        assertEquals("USER", decoded.messages().get(0).role());
        assertEquals("你好", decoded.messages().get(0).content());
        assertTrue(decoded.stateEntries().stream().anyMatch(e -> "jump_to".equals(e.key())));
    }

    @Test
    void decodeMissingPayloadReturnsError() {
        JsonNode empty = objectMapper.createObjectNode();
        AdminCheckpointStateDecoder.DecodedState decoded = decoder.decode(empty);
        // 空 object 当作明文空 map
        assertTrue(decoded.messages().isEmpty());
    }

    @Test
    void decodeNullReturnsError() {
        AdminCheckpointStateDecoder.DecodedState decoded = decoder.decode(null);
        assertEquals("state_data 为空", decoded.decodeError());
    }
}
