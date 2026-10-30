package com.wuji.assistant.agent.tool;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 领域子 Agent 每次模型回复只保留一条 {@code search_domain}，query 用传入的原问题。
 *
 * @author liudy
 */
public final class DomainSearchCallCollapser extends ModelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DomainSearchCallCollapser.class);

    private final String query;
    private final ObjectMapper objectMapper;

    public DomainSearchCallCollapser(String query, ObjectMapper objectMapper) {
        this.query = query == null ? "" : query.trim();
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public String getName() {
        return "DomainSearchCallCollapser";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        ModelResponse response = handler.call(singleCallRequest(request));
        if (response == null || !(response.getMessage() instanceof AssistantMessage assistant)) {
            return response;
        }
        AssistantMessage collapsed = collapse(assistant);
        if (collapsed == assistant) {
            return response;
        }
        return ModelResponse.of(collapsed, response.getChatResponse());
    }

    /**
     * 关掉并行工具调用。模型仍可能一次返回多条，由 {@link #collapse} 收成一条。
     */
    private ModelRequest singleCallRequest(ModelRequest request) {
        if (request == null || !(request.getOptions() instanceof OpenAiChatOptions open)) {
            return request;
        }
        ChatOptions copied = open.copy();
        if (!(copied instanceof OpenAiChatOptions next)) {
            return request;
        }
        next.setParallelToolCalls(false);
        return ModelRequest.builder(request).options(next).build();
    }

    AssistantMessage collapse(AssistantMessage message) {
        if (message == null || message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return message;
        }
        AssistantMessage.ToolCall kept = null;
        int searchCount = 0;
        for (AssistantMessage.ToolCall call : message.getToolCalls()) {
            if (call != null && DomainKnowledgeTool.SEARCH_TOOL.equals(call.name())) {
                searchCount++;
                if (kept == null) {
                    kept = call;
                }
            }
        }
        if (kept == null) {
            return message;
        }
        String arguments = argumentsFor(kept);
        boolean onlyOne = searchCount == 1 && message.getToolCalls().size() == 1
                && arguments.equals(kept.arguments());
        if (onlyOne) {
            return message;
        }
        if (searchCount > 1) {
            log.info("domain search_domain calls collapsed from {} to 1", searchCount);
        }
        String type = StringUtils.hasText(kept.type()) ? kept.type() : "function";
        AssistantMessage.ToolCall one = new AssistantMessage.ToolCall(
                kept.id(), type, DomainKnowledgeTool.SEARCH_TOOL, arguments);
        Map<String, Object> metadata = message.getMetadata() == null ? Map.of() : message.getMetadata();
        String content = message.getText() == null ? "" : message.getText();
        return AssistantMessage.builder()
                .content(content)
                .properties(metadata)
                .toolCalls(List.of(one))
                .build();
    }

    private String argumentsFor(AssistantMessage.ToolCall kept) {
        if (!StringUtils.hasText(query)) {
            return kept.arguments() == null ? "" : kept.arguments();
        }
        try {
            return objectMapper.writeValueAsString(Map.of("query", query));
        } catch (JsonProcessingException ex) {
            return kept.arguments() == null ? "" : kept.arguments();
        }
    }
}
