package com.wuji.assistant.agent.model;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * 拦截 {@code AgentLlmNode} 吞掉的 LLM 异常，重新抛出，供 {@link ModelRouter} 重试 / 切备用。
 *
 * @author liudy
 */
public class SwallowedLlmErrorInterceptor extends ModelInterceptor {

    @Override
    public String getName() {
        return "wujiSwallowedLlmError";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        ModelResponse response = handler.call(request);
        Object message = response == null ? null : response.getMessage();
        if (message instanceof AssistantMessage assistant) {
            RuntimeException swallowed = SwallowedLlmErrors.toException(assistant.getText());
            if (swallowed != null) {
                throw swallowed;
            }
        }
        return response;
    }
}
