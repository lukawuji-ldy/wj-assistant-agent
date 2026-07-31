package com.wuji.assistant.memory;

import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 短期记忆：加载会话近期消息窗口。
 *
 * @author liudy
 */
@Service
public class ShortTermMemoryService {

    private final ChatMessageRepository chatMessageRepository;

    public ShortTermMemoryService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * 加载短期上下文消息（时间正序）。
     *
     * @param userId         用户
     * @param conversationId 会话
     * @param windowSize     窗口大小
     * @return 消息列表
     */
    public List<ChatMessage> loadRecentMessages(String userId, String conversationId, int windowSize) {
        return chatMessageRepository.listRecentAsc(conversationId, userId, windowSize);
    }
}
