package com.wuji.assistant.agent.tool;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同轮多条 search_domain 收成原问题的一次调用。
 *
 * @author liudy
 */
class DomainSearchCallCollapserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parallelCalls_collapseToOriginalQuery() {
        String original = "徐凤年第二次北莽之行有什么收获";
        DomainSearchCallCollapser collapser = new DomainSearchCallCollapser(original, mapper);
        AssistantMessage raw = calls(
                call("a", "{\"query\":\"徐凤年第二次北莽之行\"}"),
                call("b", "{\"query\":\"徐凤年北莽收获\"}"));
        OpenAiChatOptions options = OpenAiChatOptions.builder().model("m").parallelToolCalls(true).build();
        AtomicReference<Boolean> parallel = new AtomicReference<>();
        ModelResponse response = collapser.interceptModel(request(original, options), req -> {
            parallel.set(((OpenAiChatOptions) req.getOptions()).getParallelToolCalls());
            return ModelResponse.of(raw);
        });

        assertEquals(Boolean.FALSE, parallel.get());
        AssistantMessage out = (AssistantMessage) response.getMessage();
        assertEquals(1, out.getToolCalls().size());
        AssistantMessage.ToolCall kept = out.getToolCalls().get(0);
        assertEquals("a", kept.id());
        assertEquals(DomainKnowledgeTool.SEARCH_TOOL, kept.name());
        assertTrue(kept.arguments().contains(original));
        assertFalse(kept.arguments().contains("北莽收获"));
    }

    @Test
    void singleRewrittenQuery_isRestored() throws Exception {
        String original = "徐凤年第二次北莽之行有什么收获";
        DomainSearchCallCollapser collapser = new DomainSearchCallCollapser(original, mapper);
        AssistantMessage out = collapser.collapse(calls(call("a", "{\"query\":\"徐凤年第二次北莽之行\"}")));
        assertEquals(1, out.getToolCalls().size());
        assertEquals(mapper.writeValueAsString(java.util.Map.of("query", original)), out.getToolCalls().get(0).arguments());
    }

    @Test
    void alreadyOriginal_isUnchanged() throws Exception {
        String original = "价格";
        String arguments = mapper.writeValueAsString(java.util.Map.of("query", original));
        DomainSearchCallCollapser collapser = new DomainSearchCallCollapser(original, mapper);
        AssistantMessage raw = calls(call("a", arguments));
        assertSame(raw, collapser.collapse(raw));
    }

    @Test
    void blankOriginal_keepsFirstArguments() {
        DomainSearchCallCollapser collapser = new DomainSearchCallCollapser("  ", mapper);
        AssistantMessage out = collapser.collapse(calls(
                call("a", "{\"query\":\"甲\"}"),
                call("b", "{\"query\":\"乙\"}")));
        assertEquals(1, out.getToolCalls().size());
        assertEquals("{\"query\":\"甲\"}", out.getToolCalls().get(0).arguments());
    }

    @Test
    void noToolCall_isUnchanged() {
        DomainSearchCallCollapser collapser = new DomainSearchCallCollapser("价格", mapper);
        AssistantMessage raw = new AssistantMessage("没有检索");
        assertSame(raw, collapser.collapse(raw));
    }

    private static ModelRequest request(String text, OpenAiChatOptions options) {
        return ModelRequest.builder()
                .messages(List.of(new UserMessage(text)))
                .options(options)
                .build();
    }

    private static AssistantMessage calls(AssistantMessage.ToolCall... toolCalls) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCalls))
                .build();
    }

    private static AssistantMessage.ToolCall call(String id, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", DomainKnowledgeTool.SEARCH_TOOL, arguments);
    }
}
