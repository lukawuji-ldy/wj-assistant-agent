package com.wuji.assistant.agent.observability;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 从 ChatResponse 提取 token 用量。
 *
 * @author liudy
 */
public final class TokenUsageExtractor {

    private TokenUsageExtractor() {
    }

    /**
     * @param response ChatResponse，可为 null
     * @return [prompt, completion]，缺省则为 null 元素
     */
    public static Integer[] fromChatResponse(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return new Integer[]{null, null};
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return new Integer[]{null, null};
        }
        return new Integer[]{usage.getPromptTokens(), usage.getCompletionTokens()};
    }
}
