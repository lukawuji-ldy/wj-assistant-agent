package com.wuji.assistant.agent.tool;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLink;

import java.util.Map;

/**
 * 父 Agent 调用 {@code knowledge_search} 前写入子线程链接，返回后从本次请求的 context 删除。
 * {@code ToolCallRequest} 持有的是 metadata 的副本，写入副本即可被 {@code AgentToolNode} 带进工具上下文。
 *
 * @author liudy
 */
public final class KnowledgeSearchLinkInterceptor extends ToolInterceptor {

    @Override
    public String getName() {
        return "KnowledgeSearchLink";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        if (handler == null) {
            return null;
        }
        if (request == null || !DomainKnowledgeTool.TOOL_NAME.equals(request.getToolName())) {
            return handler.call(request);
        }
        Map<String, Object> context = request.getContext();
        if (context == null) {
            return handler.call(request);
        }
        Object parent = context.get(AgentCheckpointLink.PARENT_THREAD_KEY);
        String parentThread = parent instanceof String text ? text : "";
        AgentCheckpointLink link = new AgentCheckpointLink(
                parentThread,
                request.getToolCallId() == null ? "" : request.getToolCallId(),
                AgentCheckpointLink.newChildThreadName());
        context.put(AgentCheckpointLink.META_KEY, link);
        try {
            return handler.call(request);
        } finally {
            context.remove(AgentCheckpointLink.META_KEY);
        }
    }
}
