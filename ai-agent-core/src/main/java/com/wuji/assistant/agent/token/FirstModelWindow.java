package com.wuji.assistant.agent.token;

import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

/**
 * 本轮第一次装进模型的窗口。供审计、裁剪说明和上下文超限重试读取。
 *
 * @author liudy
 */
public final class FirstModelWindow {

    public static final String META_KEY = "wuji.contextFit";

    private List<Message> initialMessages;
    private String initialSystem = "";
    private int estimatedPromptTokens;
    private Map<String, Object> truncation = Map.of();

    public synchronized void accept(ConversationTrimmer.TrimmedRequest result) {
        if (result == null) {
            return;
        }
        if (initialMessages == null) {
            initialMessages = result.messages();
            initialSystem = result.systemPrompt() == null ? "" : result.systemPrompt();
            estimatedPromptTokens = Math.max(0, result.estimatedPromptTokens());
        }
        if (result.truncationInfo() != null && !result.truncationInfo().isEmpty()) {
            truncation = result.truncationInfo();
        }
    }

    public synchronized List<Message> initialMessages() {
        return initialMessages == null ? List.of() : initialMessages;
    }

    public synchronized String initialSystem() {
        return initialSystem;
    }

    public synchronized int estimatedPromptTokens() {
        return estimatedPromptTokens;
    }

    public synchronized Map<String, Object> truncation() {
        return truncation;
    }
}
