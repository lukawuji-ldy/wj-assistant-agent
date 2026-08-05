package com.wuji.assistant.agent.model;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * LlmConfigRepository kind 校验测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class LlmConfigRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void requireActive_rejectsWrongKind() {
        LlmConfigRecord chat = new LlmConfigRecord();
        chat.setConfigId("llm_primary");
        chat.setModelKind(LlmConfigRecord.KIND_CHAT);
        chat.setModel("gpt-4o-mini");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("llm_primary")))
                .thenReturn(List.of(chat));

        LlmConfigRepository repo = new LlmConfigRepository(jdbcTemplate);
        WujiException ex = assertThrows(WujiException.class,
                () -> repo.requireActive("llm_primary", LlmConfigRecord.KIND_EMBEDDING));
        assertEquals(ErrorCode.MODEL_UNAVAILABLE, ex.getErrorCode());
    }

    @Test
    void requireActive_acceptsMatchingKind() {
        LlmConfigRecord emb = new LlmConfigRecord();
        emb.setConfigId("llm_embedding");
        emb.setModelKind(LlmConfigRecord.KIND_EMBEDDING);
        emb.setModel("text-embedding-3-small");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("llm_embedding")))
                .thenReturn(List.of(emb));

        LlmConfigRepository repo = new LlmConfigRepository(jdbcTemplate);
        LlmConfigRecord got = repo.requireActive("llm_embedding", LlmConfigRecord.KIND_EMBEDDING);
        assertEquals("text-embedding-3-small", got.getModel());
    }
}
