package com.wuji.assistant.agent.prompt;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.wuji.assistant.agent.token.SystemPromptHolder;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link #META_SYSTEM_PROMPT} 是当轮系统提示的 metadata 键。
 * 调用方把 {@link com.wuji.assistant.agent.token.SystemPromptHolder} 放进 RunnableConfig，
 * {@code AgentLlmNode} 带入 {@code ModelRequest.context}；{@code TokenBudgetEnforcer} 读它，压缩后回写。
 * <p>
 * {@link #interceptModel} 会按该键注入 system，并剥离 messages 中的 {@code SystemMessage}。
 * 本拦截器排在 {@code TokenBudgetInterceptor} 之前，模型收到的是预算执行后写回的请求。
 *
 * @author liudy
 */
public class WujiSystemPromptInterceptor extends ModelInterceptor {

    /** RunnableConfig metadata / ModelRequest.context 键 */
    public static final String META_SYSTEM_PROMPT = "wuji.systemPrompt";

    @Override
    public String getName() {
        return "wujiSystemPrompt";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        String fromMeta = readSystemPrompt(request.getContext());
        List<Message> cleaned = stripSystemMessages(request.getMessages());
        SystemMessage system = StringUtils.hasText(fromMeta)
                ? new SystemMessage(fromMeta)
                : request.getSystemMessage();
        ModelRequest next = ModelRequest.builder(request)
                .messages(cleaned)
                .systemMessage(system)
                .build();
        return handler.call(next);
    }

    private static String readSystemPrompt(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object v = context.get(META_SYSTEM_PROMPT);
        if (v instanceof SystemPromptHolder holder && StringUtils.hasText(holder.get())) {
            return holder.get();
        }
        return v instanceof String s && StringUtils.hasText(s) ? s : null;
    }

    private static List<Message> stripSystemMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Message> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            if (!(m instanceof SystemMessage)) {
                out.add(m);
            }
        }
        return out;
    }
}
