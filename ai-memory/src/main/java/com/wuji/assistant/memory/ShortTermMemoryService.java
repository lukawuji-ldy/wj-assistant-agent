package com.wuji.assistant.memory;

import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 短期记忆：watermark 窗口 + 可选摘要前置。
 *
 * @author liudy
 */
@Service
public class ShortTermMemoryService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;

    public ShortTermMemoryService(ChatMessageRepository chatMessageRepository,
                                  ConversationRepository conversationRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
    }

    /**
     * 兼容旧调用：仅返回消息窗口。
     */
    public List<ChatMessage> loadRecentMessages(String userId, String conversationId, int windowSize) {
        return loadContext(userId, conversationId, windowSize, 8000).messages();
    }

    /**
     * 按 watermark + token 预算加载短期上下文。
     *
     * @param userId         用户
     * @param conversationId 会话
     * @param maxMessages    最大消息条数
     * @param maxTokens      token 预算
     * @return 摘要 + 消息
     */
    public ShortTermContext loadContext(String userId, String conversationId, int maxMessages, int maxTokens) {
        Conversation conversation = conversationRepository.requireOwned(userId, conversationId);
        int limit = Math.max(1, maxMessages);
        List<ChatMessage> window;
        if (conversation.getSummaryUntilTime() != null) {
            window = chatMessageRepository.listAfterWatermarkAsc(
                    conversationId, userId, conversation.getSummaryUntilTime(), limit);
        } else {
            window = chatMessageRepository.listRecentAsc(conversationId, userId, limit);
        }
        List<ChatMessage> trimmed = trimByTokenBudget(window, Math.max(1, maxTokens));
        String summary = StringUtils.hasText(conversation.getSummary()) ? conversation.getSummary() : null;
        return new ShortTermContext(summary, trimmed);
    }

    /**
     * 从新到旧累加 token，超预算则截断更旧消息。
     */
    static List<ChatMessage> trimByTokenBudget(List<ChatMessage> chronological, int maxTokens) {
        if (chronological == null || chronological.isEmpty()) {
            return List.of();
        }
        int total = 0;
        int cutFrom = 0;
        for (int i = chronological.size() - 1; i >= 0; i--) {
            total += Math.max(0, chronological.get(i).getTokenCount());
            if (total > maxTokens) {
                cutFrom = i + 1;
                break;
            }
        }
        if (cutFrom <= 0) {
            return List.copyOf(chronological);
        }
        return new ArrayList<>(chronological.subList(cutFrom, chronological.size()));
    }
}
