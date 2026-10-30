package com.wuji.assistant.agent.token;

import com.wuji.assistant.common.token.TokenEstimator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.definition.DefaultToolDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 消息开销与工具 schema 计数。
 *
 * @author liudy
 */
class TokenCounterTest {

    @BeforeEach
    void chars() {
        TokenEstimator.setProvider("char-count", text -> text == null || text.isEmpty() ? 0 : text.length());
    }

    @AfterEach
    void clear() {
        TokenEstimator.setProvider("char-count", null);
    }

    @Test
    void countMessages_addsRoleOverheadAndTail() {
        TokenCounter counter = TokenCounterRegistry.forConfig("llm_primary");
        int counted = counter.countMessages(List.of(new UserMessage("abcd")));
        assertEquals(3 + 5 + 4 + 3, counted);
    }

    @Test
    void countTools_includesNameDescriptionAndSchema() {
        TokenCounter counter = TokenCounterRegistry.forConfig("llm_primary");
        int counted = counter.countTools(List.of(DefaultToolDefinition.builder()
                .name("echo")
                .description("ping")
                .inputSchema("{}")
                .build()));
        assertTrue(counted >= "echo".length() + "ping".length() + "{}".length());
    }
}
