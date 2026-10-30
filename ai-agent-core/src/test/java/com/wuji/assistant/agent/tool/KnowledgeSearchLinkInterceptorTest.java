package com.wuji.assistant.agent.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLink;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * knowledge_search 调用期间才把子线程链接放进 metadata。
 *
 * @author liudy
 */
class KnowledgeSearchLinkInterceptorTest {

    @Test
    void knowledgeSearch_stashesLinkUntilReturn() {
        Map<String, Object> context = new HashMap<>();
        context.put(AgentCheckpointLink.PARENT_THREAD_KEY, "u1:c1");
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName(DomainKnowledgeTool.TOOL_NAME)
                .toolCallId("call-1")
                .arguments("{}")
                .context(context)
                .build();
        AgentCheckpointLink[] seen = new AgentCheckpointLink[1];
        new KnowledgeSearchLinkInterceptor().interceptToolCall(request, req -> {
            Object value = req.getContext().get(AgentCheckpointLink.META_KEY);
            seen[0] = value instanceof AgentCheckpointLink link ? link : null;
            return new ToolCallResponse("ok", req.getToolName(), req.getToolCallId());
        });
        assertEquals("u1:c1", seen[0].parentThreadName());
        assertEquals("call-1", seen[0].parentToolCallId());
        assertTrue(seen[0].childThreadName().startsWith(AgentCheckpointLink.CHILD_PREFIX));
        assertNull(request.getContext().get(AgentCheckpointLink.META_KEY));
    }

    @Test
    void toolContextCopy_isVisibleWhenParentMetadataHasNoLink() {
        RunnableConfig config = RunnableConfig.builder()
                .threadId("u1:c1")
                .addMetadata(AgentCheckpointLink.PARENT_THREAD_KEY, "u1:c1")
                .build();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName(DomainKnowledgeTool.TOOL_NAME)
                .toolCallId("call-9")
                .arguments("{}")
                .context(config.metadata().orElseThrow())
                .build();
        AgentCheckpointLink[] seen = new AgentCheckpointLink[1];
        new KnowledgeSearchLinkInterceptor().interceptToolCall(request, req -> {
            Map<String, Object> toolContextMap = new HashMap<>();
            toolContextMap.putAll(req.getContext());
            toolContextMap.put("_AGENT_CONFIG_", config);
            seen[0] = DomainKnowledgeTool.linkOf(new ToolContext(toolContextMap));
            return new ToolCallResponse("ok", req.getToolName(), req.getToolCallId());
        });
        assertEquals("u1:c1", seen[0].parentThreadName());
        assertEquals("call-9", seen[0].parentToolCallId());
        assertTrue(seen[0].childThreadName().startsWith(AgentCheckpointLink.CHILD_PREFIX));
        assertFalse(config.metadata().orElseThrow().containsKey(AgentCheckpointLink.META_KEY));
    }

    @Test
    void otherTool_leavesContextUntouched() {
        Map<String, Object> context = new HashMap<>();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("echo_ping")
                .toolCallId("call-2")
                .arguments("{}")
                .context(context)
                .build();
        new KnowledgeSearchLinkInterceptor().interceptToolCall(request,
                req -> new ToolCallResponse("ok", req.getToolName(), req.getToolCallId()));
        assertFalse(context.containsKey(AgentCheckpointLink.META_KEY));
    }
}
