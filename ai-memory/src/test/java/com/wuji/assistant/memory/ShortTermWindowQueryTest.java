package com.wuji.assistant.memory;

import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短期窗口仍读取 watermark 之后的最新一段。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class ShortTermWindowQueryTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ConversationRepository conversationRepository;

    @Test
    void loadContext_usesNewestWindowAfterWatermark() {
        OffsetDateTime until = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        Conversation conversation = new Conversation();
        conversation.setConversationId("c1");
        conversation.setSummaryUntilTime(until);
        when(conversationRepository.requireOwned("u1", "c1")).thenReturn(conversation);
        ChatMessage newest = new ChatMessage();
        newest.setMessageId("newest");
        newest.setTokenCount(1);
        newest.setContent("hi");
        when(chatMessageRepository.listAfterWatermarkAsc("c1", "u1", until, 20))
                .thenReturn(List.of(newest));

        ShortTermMemoryService service = new ShortTermMemoryService(chatMessageRepository, conversationRepository);
        ShortTermContext context = service.loadContext("u1", "c1", 20);

        assertEquals("newest", context.messages().get(0).getMessageId());
        verify(chatMessageRepository).listAfterWatermarkAsc(eq("c1"), eq("u1"), eq(until), eq(20));
        verify(chatMessageRepository, never()).listOldestAfterWatermarkAsc(any(), any(), any(), anyInt());
    }

    @Test
    void loadContext_keepsMessagesWhenTokenCountIsLarge() {
        Conversation conversation = new Conversation();
        conversation.setConversationId("c1");
        when(conversationRepository.requireOwned("u1", "c1")).thenReturn(conversation);
        when(chatMessageRepository.listRecentAsc("c1", "u1", 20))
                .thenReturn(List.of(message("older", 9000), message("newer", 9000)));

        ShortTermMemoryService service = new ShortTermMemoryService(chatMessageRepository, conversationRepository);
        ShortTermContext context = service.loadContext("u1", "c1", 20);

        assertEquals(List.of("older", "newer"),
                context.messages().stream().map(ChatMessage::getMessageId).toList());
    }

    private static ChatMessage message(String messageId, int tokenCount) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setTokenCount(tokenCount);
        message.setContent("x");
        return message;
    }
}
