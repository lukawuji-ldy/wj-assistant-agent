package com.wuji.assistant.agent.stream;

import com.wuji.assistant.agent.model.SwallowedLlmErrors;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

/**
 * 将 ReactAgent 消息流映射为面向用户的文本增量。
 *
 * @author liudy
 */
public final class AgentStreamBridge {

    private AgentStreamBridge() {
    }

    /**
     * 仅透出助手文本；非 AssistantMessage 忽略。
     * 框架把 LLM 异常写成 {@code Exception: ...} 时转为 Flux error，避免当成 delta 推给用户。
     *
     * @param messages ReactAgent.streamMessages 输出
     * @return 文本增量流
     */
    public static Flux<String> toContentDeltas(Flux<Message> messages) {
        return messages
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .mapNotNull(AssistantMessage::getText)
                .filter(text -> !text.isEmpty())
                .handle((text, sink) -> {
                    RuntimeException swallowed = SwallowedLlmErrors.toException(text);
                    if (swallowed != null) {
                        sink.error(swallowed);
                        return;
                    }
                    sink.next(text);
                });
    }
}
