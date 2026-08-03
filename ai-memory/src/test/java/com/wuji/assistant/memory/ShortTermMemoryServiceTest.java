package com.wuji.assistant.memory;

import com.wuji.assistant.memory.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 短记忆 token 预算截断单测。
 *
 * @author liudy
 */
class ShortTermMemoryServiceTest {

    @Test
    void trimByTokenBudget_keepsNewest() {
        ChatMessage a = msg(10);
        ChatMessage b = msg(10);
        ChatMessage c = msg(10);
        List<ChatMessage> trimmed = ShortTermMemoryService.trimByTokenBudget(List.of(a, b, c), 20);
        assertEquals(2, trimmed.size());
        assertEquals(b, trimmed.get(0));
        assertEquals(c, trimmed.get(1));
    }

    private static ChatMessage msg(int tokens) {
        ChatMessage m = new ChatMessage();
        m.setTokenCount(tokens);
        m.setContent("x");
        return m;
    }
}
