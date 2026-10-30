package com.wuji.assistant.server.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.prompt.WujiSystemPromptInterceptor;
import com.wuji.assistant.agent.token.CallBudget;
import com.wuji.assistant.agent.token.CompressOnce;
import com.wuji.assistant.agent.token.TokenBudgetEnforcer;
import com.wuji.assistant.agent.token.SystemPromptHolder;
import com.wuji.assistant.agent.token.TurnRetrievalCache;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonSafeMcpMetaConverter} 单测。
 *
 * @author liudy
 */
class JsonSafeMcpMetaConverterTest {

    private final JsonSafeMcpMetaConverter converter = new JsonSafeMcpMetaConverter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convert_dropsProcessObjectsAndKeepsJsonValues() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("city", "上海");
        nested.put("cache", new TurnRetrievalCache());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put(TurnRetrievalCache.META_KEY, new TurnRetrievalCache());
        context.put(CallBudget.META_KEY, new CallBudget(100, 10, 20));
        context.put(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT, new SystemPromptHolder("sys"));
        context.put(CompressOnce.META_KEY, new CompressOnce());
        context.put(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, new Object());
        context.put(TokenBudgetEnforcer.USER_ID, "u1");
        context.put(TokenBudgetEnforcer.CONVERSATION_ID, "c1");
        context.put("limit", 3);
        context.put("ok", true);
        context.put("mode", Mode.READ);
        context.put("tags", List.of("a", new TurnRetrievalCache(), "b"));
        context.put("extra", nested);
        context.put("nan", Double.NaN);

        Map<String, Object> meta = converter.convert(new ToolContext(context));

        assertEquals("u1", meta.get(TokenBudgetEnforcer.USER_ID));
        assertEquals("c1", meta.get(TokenBudgetEnforcer.CONVERSATION_ID));
        assertEquals(3, meta.get("limit"));
        assertEquals(true, meta.get("ok"));
        assertEquals("READ", meta.get("mode"));
        assertEquals(List.of("a", "b"), meta.get("tags"));
        assertEquals(Map.of("city", "上海"), meta.get("extra"));
        assertFalse(meta.containsKey(TurnRetrievalCache.META_KEY));
        assertFalse(meta.containsKey(CallBudget.META_KEY));
        assertFalse(meta.containsKey(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT));
        assertFalse(meta.containsKey(CompressOnce.META_KEY));
        assertFalse(meta.containsKey(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY));
        assertFalse(meta.containsKey("nan"));

        String json = objectMapper.writeValueAsString(meta);
        assertTrue(json.contains("上海"));
        assertFalse(json.contains("TurnRetrievalCache"));
    }

    @Test
    void convert_emptyWhenContextMissing() {
        assertEquals(Map.of(), converter.convert(null));
        assertEquals(Map.of(), converter.convert(new ToolContext(Map.of())));
    }

    private enum Mode {
        READ
    }
}
