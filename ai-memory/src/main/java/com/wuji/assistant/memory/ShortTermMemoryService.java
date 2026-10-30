package com.wuji.assistant.memory;

import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        return loadContext(userId, conversationId, windowSize).messages();
    }

    /**
     * 按 watermark 与消息条数加载短期上下文。是否放得进模型由入模门按 {@code W - R} 决定。
     *
     * @param userId         用户
     * @param conversationId 会话
     * @param maxMessages    最大消息条数
     * @return 摘要 + 消息
     */
    public ShortTermContext loadContext(String userId, String conversationId, int maxMessages) {
        Conversation conversation = conversationRepository.requireOwned(userId, conversationId);
        int limit = Math.max(1, maxMessages);
        List<ChatMessage> window;
        if (conversation.getSummaryUntilTime() != null) {
            window = chatMessageRepository.listAfterWatermarkAsc(
                    conversationId, userId, conversation.getSummaryUntilTime(), limit);
        } else {
            window = chatMessageRepository.listRecentAsc(conversationId, userId, limit);
        }
        String summary = StringUtils.hasText(conversation.getSummary()) ? conversation.getSummary() : null;
        return new ShortTermContext(summary, window == null ? List.of() : List.copyOf(window));
    }
}
