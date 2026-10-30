package com.wuji.assistant.agent.token;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.token.TokenEstimator;
import com.wuji.assistant.memory.retrieve.MemoryItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 超限只丢最旧历史轮。系统、记忆、摘要和当轮问题保持原样。
 *
 * @author liudy
 */
class ConversationTrimmerTest {

    private final ConversationTrimmer trimmer = new ConversationTrimmer();

    @BeforeEach
    void chars() {
        TokenEstimator.setProvider("char-count", text -> text == null || text.isEmpty() ? 0 : text.length());
    }

    @AfterEach
    void clear() {
        TokenEstimator.setProvider("char-count", null);
    }

    @Test
    void underBudget_keepsHistoryAndOmitsTruncation() {
        ContextDraft draft = draft("sys", "q", List.of(new UserMessage("u1"), new AssistantMessage("a1")));
        ConversationTrimmer.TrimmedRequest trimmed = trim(draft, 10_000);
        assertFalse(trimmed.overBudget());
        assertNull(trimmed.truncationInfo());
        assertEquals(0, trimmed.droppedUserMessages());
        assertTrue(trimmed.systemPrompt().contains("sys"));
        assertEquals("q", trimmed.messages().get(trimmed.messages().size() - 1).getText());
    }

    @Test
    void dropOldestTurn_keepsCurrentUserAndMemory() {
        ContextDraft draft = new ContextDraft(
                "sys", "", "now", "", "摘要不动",
                List.of(new MemoryItem("a", "profile", "keep-memory", 0.9)),
                List.of(
                        new UserMessage("u".repeat(400)),
                        new AssistantMessage("a".repeat(400)),
                        new UserMessage("v".repeat(400)),
                        new AssistantMessage("b".repeat(400))));
        ConversationTrimmer.TrimmedRequest trimmed = trim(draft, 500);
        assertTrue(trimmed.droppedUserMessages() > 0);
        assertInstanceOf(UserMessage.class, trimmed.messages().get(0));
        assertEquals("now", trimmed.messages().get(trimmed.messages().size() - 1).getText());
        assertTrue(trimmed.systemPrompt().contains("keep-memory"));
        assertTrue(trimmed.systemPrompt().contains("摘要不动"));
        assertEquals("dropped", ((List<?>) trimmed.truncationInfo().get("segments")).isEmpty()
                ? "" : ((java.util.Map<?, ?>) ((List<?>) trimmed.truncationInfo().get("segments")).get(0)).get("action"));
    }

    @Test
    void historyExhausted_throwsBadRequest() {
        ContextDraft draft = new ContextDraft("S".repeat(500), "", "q", "", "", List.of(), List.of());
        WujiException ex = assertThrows(WujiException.class, () -> trim(draft, 40));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void count_doesNotDrop() {
        ContextDraft draft = draft("sys", "q", List.of(new UserMessage("u".repeat(200)), new AssistantMessage("a")));
        ConversationTrimmer.TrimmedRequest assembled = trimmer.assemble(draft);
        int counted = ConversationTrimmer.count(assembled.systemPrompt(), assembled.messages(), 0, counter());
        assertTrue(counted > 30);
        assertEquals(0, assembled.droppedUserMessages());
        assertTrue(assembled.messages().stream().anyMatch(message -> message.getText() != null && message.getText().startsWith("uu")));
    }

    @Test
    void dropAtLeastOne_dropsATurnThatAlreadyFits() {
        List<Message> messages = List.of(
                new UserMessage("old"),
                new AssistantMessage("reply"),
                new UserMessage("now"));
        ConversationTrimmer.TrimmedRequest trimmed = trimmer.trimConversationDroppingAtLeastOne(
                "sys", messages, 10_000, 0, counter());
        assertEquals(1, trimmed.droppedUserMessages());
        assertEquals("now", trimmed.messages().get(0).getText());
    }

    @Test
    void liveMessages_doNotSplitToolTail() {
        List<Message> messages = List.of(
                new UserMessage("x".repeat(300)),
                new AssistantMessage("old-answer"),
                new UserMessage("current"),
                new AssistantMessage("tool-call"));
        ConversationTrimmer.TrimmedRequest trimmed = trimmer.trimConversation("sys", messages, 200, 10, counter());
        assertEquals("current", trimmed.messages().get(0).getText());
        assertTrue(trimmed.messages().stream().anyMatch(message -> "tool-call".equals(message.getText())));
    }

    private ConversationTrimmer.TrimmedRequest trim(ContextDraft draft, int inputBudget) {
        ConversationTrimmer.TrimmedRequest assembled = trimmer.assemble(draft);
        return trimmer.trimConversation(assembled.systemPrompt(), assembled.messages(), inputBudget, 0, counter());
    }

    private static ContextDraft draft(String system, String user, List<Message> history) {
        return new ContextDraft(system, "", user, "", "", List.of(), history);
    }

    private static TokenCounter counter() {
        return TokenCounterRegistry.forConfig("llm");
    }
}
