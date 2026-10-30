package com.wuji.assistant.agent.token;

import com.wuji.assistant.memory.retrieve.MemoryItem;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * prepare 装载后的稳定前缀。优化器复制条目，不改这份原稿，便于 failover 重装。
 *
 * @param systemRules 系统规则
 * @param userPrefix  当轮问题模板里、用户原文之前的部分
 * @param userRaw     用户原文
 * @param userSuffix  用户原文之后的模板
 * @param summary     会话摘要正文
 * @param memoryItems 长期记忆条目
 * @param history     watermark 之后的历史，不含当轮问题
 * @author liudy
 */
public record ContextDraft(
        String systemRules,
        String userPrefix,
        String userRaw,
        String userSuffix,
        String summary,
        List<MemoryItem> memoryItems,
        List<Message> history
) {
    public ContextDraft {
        memoryItems = memoryItems == null ? List.of() : List.copyOf(memoryItems);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public static ContextDraft empty() {
        return new ContextDraft("", "", "", "", "", List.of(), List.of());
    }

    public List<MemoryItem> memoryItemsCopy() {
        return new ArrayList<>(memoryItems);
    }

    public List<Message> historyCopy() {
        return new ArrayList<>(history);
    }
}
