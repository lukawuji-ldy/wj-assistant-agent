package com.wuji.assistant.agent.token;

import com.wuji.assistant.common.token.TokenEstimator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

/**
 * {@link TokenEstimator} 上的消息与工具计数。
 *
 * @author liudy
 */
public final class EstimatingTokenCounter implements TokenCounter {

    private static final int MESSAGE_OVERHEAD = 3;
    private static final int TAIL_OVERHEAD = 3;

    private final String configId;

    public EstimatingTokenCounter(String configId) {
        this.configId = configId == null ? "" : configId.trim();
    }

    @Override
    public String tokenizerId() {
        return configId.isEmpty() ? TokenEstimator.providerId() : configId;
    }

    @Override
    public int countText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (configId.isEmpty()) {
            return TokenEstimator.estimate(text);
        }
        return TokenEstimator.estimate(text, configId);
    }

    @Override
    public int countMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message message : messages) {
            total += messageTokens(message);
        }
        return total + TAIL_OVERHEAD;
    }

    @Override
    public int countTools(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ToolDefinition tool : tools) {
            if (tool == null) {
                continue;
            }
            total += countText(tool.name());
            total += countText(tool.description());
            total += countText(tool.inputSchema());
        }
        return total;
    }

    /**
     * 单条消息的正文与角色开销，不含整段收尾。
     *
     * @param message 消息
     * @return token 数
     */
    public int messageTokens(Message message) {
        if (message == null) {
            return 0;
        }
        String text = message.getText();
        return MESSAGE_OVERHEAD + roleOverhead(message) + countText(text);
    }

    private static int roleOverhead(Message message) {
        if (message instanceof SystemMessage) {
            return 6;
        }
        if (message instanceof UserMessage) {
            return 5;
        }
        if (message instanceof AssistantMessage) {
            return 4;
        }
        return 4;
    }
}
