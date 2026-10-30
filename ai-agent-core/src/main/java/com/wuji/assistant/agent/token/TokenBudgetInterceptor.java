package com.wuji.assistant.agent.token;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 每次模型调用前执行 token 预算，再把裁剪结果写回请求。
 *
 * @author liudy
 */
public final class TokenBudgetInterceptor extends ModelInterceptor {

    private final String configId;
    private final int toolSchemaTokens;
    private final TokenBudgetEnforcer enforcer;

    public TokenBudgetInterceptor(String configId, int toolSchemaTokens, TokenBudgetEnforcer enforcer) {
        this.configId = configId == null ? "" : configId;
        this.toolSchemaTokens = Math.max(0, toolSchemaTokens);
        this.enforcer = enforcer;
    }

    @Override
    public String getName() {
        return "TokenBudget";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        if (enforcer == null) {
            return handler.call(request);
        }
        TokenBudgetEnforcer.EnforcedRequest enforced = enforcer.enforce(configId, toolSchemaTokens, request);
        ModelRequest.Builder builder = ModelRequest.builder(request)
                .systemMessage(new SystemMessage(enforced.systemPrompt()))
                .messages(enforced.messages());
        capOutput(request, builder, enforced.outputCap());
        return handler.call(builder.build());
    }

    private static void capOutput(ModelRequest request, ModelRequest.Builder builder, int outputCap) {
        if (outputCap <= 0 || request.getOptions() == null) {
            return;
        }
        if (request.getOptions() instanceof OpenAiChatOptions open) {
            Integer current = open.getMaxTokens();
            open.setMaxTokens(current == null ? outputCap : Math.min(current, outputCap));
            builder.options(open);
        }
    }
}
