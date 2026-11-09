package com.wuji.assistant.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.memory.SummaryService;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LLM 摘要失败或超时后仍写出规则摘要。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class LlmSummaryOrchestratorTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private ModelRouter modelRouter;

    private WujiMemoryProperties memoryProperties;
    private LlmSummaryOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        memoryProperties = new WujiMemoryProperties();
        memoryProperties.getShort().setCompressMessageThreshold(4);
        memoryProperties.getShort().setKeepRecentMessages(2);
        memoryProperties.getShort().setCompressTokenThreshold(12000);
        memoryProperties.getShort().setSummaryTimeout(Duration.ofSeconds(2));
        SummaryService summaryService = new SummaryService(
                conversationRepository, chatMessageRepository, new ObjectMapper());
        orchestrator = new LlmSummaryOrchestrator(
                memoryProperties,
                summaryService,
                promptTemplateService,
                modelRouter,
                new ObjectMapper(),
                Schedulers.boundedElastic());
        Conversation conversation = new Conversation();
        conversation.setConversationId("c1");
        conversation.setMessageCount(4);
        conversation.setSummary("{\"goal\":\"旧目标\",\"facts\":[\"旧事实\"],\"pending_tasks\":[]}");
        when(conversationRepository.requireOwned("u1", "c1")).thenReturn(conversation);
        when(chatMessageRepository.countAfterWatermark(eq("c1"), eq("u1"), isNull(), isNull())).thenReturn(4L);
        when(chatMessageRepository.sumTokenCountAfterWatermark(eq("c1"), eq("u1"), isNull(), isNull())).thenReturn(10L);
        when(chatMessageRepository.listOldestAfterWatermarkAsc(eq("c1"), eq("u1"), isNull(), isNull(), eq(6)))
                .thenReturn(List.of(
                        message("m1", "需要记住的旧事实甲"),
                        message("m2", "需要记住的旧事实乙"),
                        message("m3", "最近保留一"),
                        message("m4", "最近保留二")));
        when(promptTemplateService.loadAndRender(anyString(), any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(2));
    }

    @Test
    void llmFailure_fallsBackToRuleSummary() throws Exception {
        when(modelRouter.callContent(anyList(), any())).thenThrow(new IllegalStateException("boom"));

        orchestrator.compress("u1", "c1");

        String json = capturedSummary();
        assertFalse(json.isBlank());
        assertTrue(json.contains("需要记住的旧事实甲"), json);
        assertTrue(json.contains("旧事实"), json);
        assertFalse(json.contains("最近保留一"), json);
    }

    @Test
    void llmTimeout_fallsBackToRuleSummary() throws Exception {
        memoryProperties.getShort().setSummaryTimeout(Duration.ofMillis(40));
        when(modelRouter.callContent(anyList(), any())).thenAnswer(inv -> {
            Thread.sleep(400);
            return new ModelRouter.RoutedResult<>(
                    "{\"goal\":\"来自模型\",\"facts\":[],\"decisions\":[],\"pending_tasks\":[]}", null);
        });

        orchestrator.compress("u1", "c1");

        String json = capturedSummary();
        assertTrue(json.contains("需要记住的旧事实甲"), json);
        assertFalse(json.contains("来自模型"), json);
    }

    @Test
    void llmSuccess_commitsModelJson() throws Exception {
        when(modelRouter.callContent(anyList(), any())).thenReturn(new ModelRouter.RoutedResult<>("""
                ```json
                {"goal":"来自模型","facts":["模型事实"],"decisions":[],"pending_tasks":["待办"]}
                ```
                """, null));

        orchestrator.compress("u1", "c1");

        String json = capturedSummary();
        assertTrue(json.contains("来自模型"), json);
        assertTrue(json.contains("模型事实"), json);
        assertTrue(json.contains("待办"), json);
        assertFalse(json.contains("需要记住的旧事实甲"), json);
    }

    /**
     * P0：LLM 摘要四字段必须与规则路径同口径限流（facts≤20 / decisions≤10），并去重，防滚动合并递归膨胀。
     */
    @Test
    void llmSuccess_capsSummaryFields() throws Exception {
        StringBuilder facts = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            if (i > 1) {
                facts.append(',');
            }
            facts.append("\"f").append(i).append('"');
        }
        facts.append(",\"f1\""); // 重复项应被去重
        StringBuilder decisions = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            if (i > 1) {
                decisions.append(',');
            }
            decisions.append("\"d").append(i).append('"');
        }
        String modelJson = "{\"goal\":\"g\",\"facts\":[" + facts + "],\"decisions\":[" + decisions
                + "],\"pending_tasks\":[]}";
        when(modelRouter.callContent(anyList(), any()))
                .thenReturn(new ModelRouter.RoutedResult<>(modelJson, null));

        orchestrator.compress("u1", "c1");

        JsonNode root = new ObjectMapper().readTree(capturedSummary());
        JsonNode factsNode = root.get("facts");
        assertEquals(20, factsNode.size());
        assertEquals("f6", factsNode.get(0).asText());
        assertEquals("f25", factsNode.get(19).asText());
        JsonNode decisionsNode = root.get("decisions");
        assertEquals(10, decisionsNode.size());
        assertEquals("d3", decisionsNode.get(0).asText());
        assertEquals("d12", decisionsNode.get(9).asText());
    }

    private String capturedSummary() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(conversationRepository).updateSummary(eq("c1"), captor.capture(), any(), eq("m2"));
        return captor.getValue();
    }

    private static ChatMessage message(String id, String content) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(id);
        message.setRole("user");
        message.setContent(content);
        message.setCreateTime(OffsetDateTime.of(2026, 1, 1, 0, Integer.parseInt(id.substring(1)), 0, 0, ZoneOffset.UTC));
        return message;
    }
}
