package com.wuji.assistant.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * LlmCallAuditor JSONB 入参清洗单测。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class LlmCallAuditorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void record_stripsNulFromJsonbPayload() {
        AtomicReference<String> requestJson = new AtomicReference<>();
        AtomicReference<String> responseJson = new AtomicReference<>();
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any()))
                .thenAnswer(inv -> {
                    requestJson.set(inv.getArgument(18));
                    responseJson.set(inv.getArgument(19));
                    return 1;
                });

        LlmCallAuditor auditor = new LlmCallAuditor(jdbcTemplate, new ObjectMapper());
        auditor.record(new LlmCallAuditor.AuditParams(
                "trace", "conv", "msg", "user",
                "CHAT", null,
                "model", "provider",
                1, false, "SUCCESS", null, 10, 1, 1,
                Map.of("system", "sys\u0000", "user", "ask", "model", "m"),
                Map.of("content", "</mm:think>\u00001+1=2")
        ));

        assertNotNull(requestJson.get());
        assertNotNull(responseJson.get());
        assertFalse(requestJson.get().contains("\\u0000"), requestJson.get());
        assertFalse(responseJson.get().contains("\\u0000"), responseJson.get());
        assertTrue(responseJson.get().contains("1+1=2"), responseJson.get());
        assertTrue(responseJson.get().contains("</mm:think>"), responseJson.get());
    }
}
