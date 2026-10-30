package com.wuji.assistant.agent.token;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.memory.LlmSummaryOrchestrator;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.prompt.WujiSystemPromptInterceptor;
import com.wuji.assistant.common.token.TokenEstimator;
import com.wuji.assistant.memory.ShortTermContext;
import com.wuji.assistant.memory.ShortTermMemoryService;
import com.wuji.assistant.memory.model.ChatMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超限压缩和丢历史只发生在入模前的预算执行。
 *
 * @author liudy
 */
class TokenBudgetEnforcerTest {

    private final ConversationTrimmer trimmer = new ConversationTrimmer();
    private LlmSummaryOrchestrator summaryOrchestrator;
    private ShortTermMemoryService shortTermMemoryService;
    private WujiMemoryProperties memoryProperties;

    @BeforeEach
    void chars() {
        TokenEstimator.setProvider("char-count", text -> text == null || text.isEmpty() ? 0 : text.length());
        summaryOrchestrator = mock(LlmSummaryOrchestrator.class);
        shortTermMemoryService = mock(ShortTermMemoryService.class);
        memoryProperties = new WujiMemoryProperties();
    }

    @AfterEach
    void clear() {
        TokenEstimator.setProvider("char-count", null);
    }

    @Test
    void overBudget_submitsUrgentCompressThenDrops() {
        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        FirstModelWindow window = new FirstModelWindow();
        TokenBudgetEnforcer enforcer = enforcer();

        new TokenBudgetInterceptor("llm", 0, enforcer).interceptModel(
                request(window, new CompressOnce(), 80, new UserMessage("h".repeat(400)), new UserMessage("now")),
                handler(captured));

        verify(summaryOrchestrator).submitUrgent(any(), eq("u1"), eq("c1"));
        verify(summaryOrchestrator, never()).compressUrgent(anyString(), anyString(), any());
        assertEquals("now", captured.get().getMessages().get(0).getText());
        assertTrue(window.estimatedPromptTokens() > 0);
        assertEquals(Boolean.TRUE, window.truncation().get("truncated"));
    }

    @Test
    void syncCompress_reloadsSummaryAndHistory() {
        memoryProperties.getShort().setSummarySyncEnabled(true);
        when(summaryOrchestrator.compressUrgent(eq("u1"), eq("c1"), any())).thenReturn(true);
        ChatMessage kept = row("old", "user", "kept");
        ChatMessage current = row("u-now", "user", "now");
        when(shortTermMemoryService.loadContext(eq("u1"), eq("c1"), anyInt()))
                .thenReturn(new ShortTermContext("{\"goal\":\"kept\"}", List.of(kept, current)));

        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        TokenBudgetEnforcer enforcer = enforcer();
        Map<String, Object> context = context(new FirstModelWindow(), new CompressOnce(), 200);
        context.put(TokenBudgetEnforcer.USER_MESSAGE_ID, "u-now");
        context.put(TokenBudgetEnforcer.ASSISTANT_MESSAGE_ID, "a-now");

        new TokenBudgetInterceptor("llm", 0, enforcer).interceptModel(
                request(context, new UserMessage("h".repeat(400)), new UserMessage("now")),
                handler(captured));

        verify(summaryOrchestrator).compressUrgent(eq("u1"), eq("c1"), any());
        verify(summaryOrchestrator, never()).submitUrgent(any(), anyString(), anyString());
        assertEquals("kept", captured.get().getMessages().get(0).getText());
        assertEquals("now", captured.get().getMessages().get(1).getText());
        assertTrue(captured.get().getSystemMessage().getText().contains("{\"goal\":\"kept\"}"));
    }

    @Test
    void underBudget_doesNotCompress() {
        AtomicReference<ModelRequest> captured = new AtomicReference<>();
        new TokenBudgetInterceptor("llm", 0, enforcer()).interceptModel(
                request(new FirstModelWindow(), new CompressOnce(), 10_000, new UserMessage("hi")),
                handler(captured));

        verify(summaryOrchestrator, never()).submitUrgent(any(), anyString(), anyString());
        verify(summaryOrchestrator, never()).compressUrgent(anyString(), anyString(), any());
        assertEquals(1, captured.get().getMessages().size());
    }

    @Test
    void alreadyStarted_skipsSecondCompress() {
        CompressOnce once = new CompressOnce();
        assertTrue(once.tryStart());
        new TokenBudgetInterceptor("llm", 0, enforcer()).interceptModel(
                request(new FirstModelWindow(), once, 80, new UserMessage("h".repeat(400)), new UserMessage("now")),
                handler(new AtomicReference<>()));

        verify(summaryOrchestrator, never()).submitUrgent(any(), anyString(), anyString());
        verify(summaryOrchestrator, never()).compressUrgent(anyString(), anyString(), any());
    }

    private TokenBudgetEnforcer enforcer() {
        return new TokenBudgetEnforcer(
                mock(InputBudgetCalculator.class),
                trimmer,
                mock(LlmClientFactory.class),
                summaryOrchestrator,
                shortTermMemoryService,
                memoryProperties,
                Schedulers.immediate());
    }

    private static ModelCallHandler handler(AtomicReference<ModelRequest> captured) {
        return request -> {
            captured.set(request);
            return ModelResponse.of(new AssistantMessage("ok"));
        };
    }

    private static ModelRequest request(FirstModelWindow window, CompressOnce once, int budget,
                                        UserMessage... messages) {
        return request(context(window, once, budget), messages);
    }

    private static ModelRequest request(Map<String, Object> context, UserMessage... messages) {
        return ModelRequest.builder()
                .messages(List.of(messages))
                .context(context)
                .build();
    }

    private static Map<String, Object> context(FirstModelWindow window, CompressOnce once, int budget) {
        Map<String, Object> context = new HashMap<>();
        SystemPromptHolder holder = new SystemPromptHolder();
        holder.set("rules");
        context.put(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT, holder);
        context.put(CallBudget.META_KEY, new CallBudget(budget, 0, 0));
        context.put(CompressOnce.META_KEY, once);
        context.put(FirstModelWindow.META_KEY, window);
        context.put(TokenBudgetEnforcer.USER_ID, "u1");
        context.put(TokenBudgetEnforcer.CONVERSATION_ID, "c1");
        context.put(TokenBudgetEnforcer.USER_MESSAGE_ID, "u-now");
        context.put(TokenBudgetEnforcer.ASSISTANT_MESSAGE_ID, "a-now");
        return context;
    }

    private static ChatMessage row(String id, String role, String content) {
        ChatMessage row = new ChatMessage();
        row.setMessageId(id);
        row.setRole(role);
        row.setContent(content);
        return row;
    }
}
