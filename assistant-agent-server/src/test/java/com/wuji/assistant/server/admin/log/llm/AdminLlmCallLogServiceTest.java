package com.wuji.assistant.server.admin.log.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AdminLlmCallLogService 单元测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class AdminLlmCallLogServiceTest {

    @Mock
    private AdminLlmCallLogRepository repository;

    private AdminLlmCallLogService service;

    @BeforeEach
    void setUp() {
        service = new AdminLlmCallLogService(repository);
    }

    @Test
    void listRejectsInvertedTimeRange() {
        WujiException ex = assertThrows(WujiException.class, () ->
                service.list(null, null, null, null, null, null, null, null, null,
                        Instant.parse("2026-08-07T12:00:00Z"),
                        Instant.parse("2026-08-07T10:00:00Z"),
                        null, null, null, null, 1, 20));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void listNormalizesPageAndDelegates() {
        AdminLlmCallLogSummary row = new AdminLlmCallLogSummary(
                "call_1", "tr", "u1", "c1", "m1", "llm_primary", "openai",
                1, false, "SUCCESS", null, 120, 10, 20,
                OffsetDateTime.ofInstant(Instant.parse("2026-08-07T10:00:00Z"), ZoneOffset.UTC));
        when(repository.count(any())).thenReturn(1L);
        when(repository.list(any(), eq(1), eq(0))).thenReturn(List.of(row));

        AdminLlmCallLogPage page = service.list(
                "u1", null, null, null, null, null, null, "SUCCESS", false,
                null, null, null, null, null, null, 0, 0);

        assertEquals(1, page.page());
        assertEquals(1, page.size());
        assertEquals(1, page.items().size());
        assertEquals("call_1", page.items().get(0).callId());
    }

    @Test
    void getMissingThrowsNotFound() {
        when(repository.findByCallId("missing")).thenReturn(Optional.empty());
        WujiException ex = assertThrows(WujiException.class, () -> service.get("missing"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void buildWhereIncludesFilters() {
        AdminLlmCallLogRepository repo = new AdminLlmCallLogRepository(
                org.mockito.Mockito.mock(JdbcTemplate.class), new ObjectMapper());
        AdminLlmCallLogQuery query = new AdminLlmCallLogQuery(
                "u1", "c1", "m1", "call_x", "tr", "model", "prov", "SUCCESS", true,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-07T00:00:00Z"),
                10, 500, 1, 1000, 1, 20);
        AdminLlmCallLogRepository.WhereClause where = repo.buildWhere(query);
        assertTrue(where.sql().contains("user_id = ?"));
        assertTrue(where.sql().contains("is_fallback = ?"));
        assertTrue(where.sql().contains("latency_ms >= ?"));
        assertEquals(15, where.args().size());
    }
}
