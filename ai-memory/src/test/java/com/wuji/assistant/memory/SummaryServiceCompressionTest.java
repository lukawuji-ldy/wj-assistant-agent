package com.wuji.assistant.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 摘要压缩选段：最旧连续段，不改短期窗口的最新 N 条查询。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class SummaryServiceCompressionTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    private SummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new SummaryService(conversationRepository, chatMessageRepository, new ObjectMapper());
    }

    @Test
    void prepare_compressesOldestSegment_notNewestWindow() {
        Conversation conversation = conversation("c1", 40);
        when(conversationRepository.requireOwned("u1", "c1")).thenReturn(conversation);
        when(chatMessageRepository.sumTokenCountAfterWatermark(eq("c1"), eq("u1"), isNull())).thenReturn(100L);
        when(chatMessageRepository.listOldestAfterWatermarkAsc(eq("c1"), eq("u1"), isNull(), eq(40)))
                .thenReturn(messages(1, 40));

        Optional<SummaryService.CompressionBatch> batch = summaryService.prepare("u1", "c1", 30, 10, 12000);

        assertTrue(batch.isPresent());
        assertEquals(30, batch.get().toCompress().size());
        assertEquals("m1", batch.get().toCompress().get(0).getMessageId());
        assertEquals("m30", batch.get().lastCovered().getMessageId());
        verify(chatMessageRepository, never()).listAfterWatermarkAsc(any(), any(), any(), anyInt());
    }

    @Test
    void prepare_tokenThresholdTriggersWhenMessageCountIsLow() {
        Conversation conversation = conversation("c1", 5);
        when(conversationRepository.requireOwned("u1", "c1")).thenReturn(conversation);
        when(chatMessageRepository.sumTokenCountAfterWatermark(eq("c1"), eq("u1"), isNull())).thenReturn(13_000L);
        when(chatMessageRepository.listOldestAfterWatermarkAsc(eq("c1"), eq("u1"), isNull(), eq(32)))
                .thenReturn(messages(1, 5));

        Optional<SummaryService.CompressionBatch> batch = summaryService.prepare("u1", "c1", 30, 2, 12000);

        assertTrue(batch.isPresent());
        assertEquals(3, batch.get().toCompress().size());
        assertEquals("m1", batch.get().toCompress().get(0).getMessageId());
        assertEquals("m3", batch.get().lastCovered().getMessageId());
    }

    @Test
    void prepare_skipsWhenNeitherThresholdMet() {
        Conversation conversation = conversation("c1", 4);
        when(conversationRepository.requireOwned("u1", "c1")).thenReturn(conversation);
        when(chatMessageRepository.sumTokenCountAfterWatermark(eq("c1"), eq("u1"), isNull())).thenReturn(100L);

        Optional<SummaryService.CompressionBatch> batch = summaryService.prepare("u1", "c1", 30, 10, 12000);

        assertTrue(batch.isEmpty());
        verify(chatMessageRepository, never()).listOldestAfterWatermarkAsc(any(), any(), any(), anyInt());
    }

    private static Conversation conversation(String id, int messageCount) {
        Conversation conversation = new Conversation();
        conversation.setConversationId(id);
        conversation.setUserId("u1");
        conversation.setMessageCount(messageCount);
        return conversation;
    }

    private static List<ChatMessage> messages(int from, int to) {
        List<ChatMessage> list = new ArrayList<>();
        OffsetDateTime start = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        for (int i = from; i <= to; i++) {
            ChatMessage message = new ChatMessage();
            message.setMessageId("m" + i);
            message.setRole(i % 2 == 0 ? "assistant" : "user");
            message.setContent("body-" + i);
            message.setTokenCount(100);
            message.setCreateTime(start.plusMinutes(i));
            list.add(message);
        }
        return list;
    }
}
