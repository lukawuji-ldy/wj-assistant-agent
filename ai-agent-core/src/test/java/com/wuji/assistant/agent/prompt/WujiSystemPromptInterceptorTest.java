package com.wuji.assistant.agent.prompt;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WujiSystemPromptInterceptor：单 system + 剥离多余 SystemMessage。
 *
 * @author liudy
 */
class WujiSystemPromptInterceptorTest {

    @Test
    void injectsMetaSystemAndStripsSystemMessages() {
        WujiSystemPromptInterceptor interceptor = new WujiSystemPromptInterceptor();
        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        ModelCallHandler handler = req -> {
            captured.set(req);
            return ModelResponse.of(new AssistantMessage("ok"));
        };

        ModelRequest incoming = ModelRequest.builder()
                .messages(List.of(
                        new SystemMessage("stale-1"),
                        new SystemMessage("stale-2"),
                        new UserMessage("我喜欢什么颜色")))
                .context(Map.of(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT,
                        "已知用户长期记忆:\npreference.favorite_color=蓝色"))
                .build();

        interceptor.interceptModel(incoming, handler);

        ModelRequest out = captured.get();
        assertEquals(1, out.getMessages().size());
        assertTrue(out.getMessages().get(0) instanceof UserMessage);
        assertFalse(out.getMessages().stream().anyMatch(m -> m instanceof SystemMessage));
        assertEquals("已知用户长期记忆:\npreference.favorite_color=蓝色", out.getSystemMessage().getText());
    }
}
