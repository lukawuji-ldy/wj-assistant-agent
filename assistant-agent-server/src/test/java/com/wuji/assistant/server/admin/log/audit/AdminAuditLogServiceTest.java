package com.wuji.assistant.server.admin.log.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
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
 * AdminAuditLogService 单元测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @Mock
    private AdminAuditLogRepository repository;

    private AdminAuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AdminAuditLogService(repository);
    }

    @Test
    void listRejectsInvertedTimeRange() {
        WujiException ex = assertThrows(WujiException.class, () ->
                service.list(null, null, null, null,
                        Instant.parse("2026-08-07T12:00:00Z"),
                        Instant.parse("2026-08-07T10:00:00Z"),
                        1, 20));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void listNormalizesPageAndDelegates() {
        AdminAuditLogSummary row = new AdminAuditLogSummary(
                "1001", "a_admin", "admin", "UPDATE", "llm_config", "llm_primary",
                OffsetDateTime.ofInstant(Instant.parse("2026-08-07T10:00:00Z"), ZoneOffset.UTC));
        when(repository.count(any())).thenReturn(1L);
        when(repository.list(any(), eq(1), eq(0))).thenReturn(List.of(row));

        AdminAuditLogPage page = service.list(
                "a_admin", "UPDATE", "llm_config", null,
                null, null, 0, 0);

        assertEquals(1, page.page());
        assertEquals(1, page.size());
        assertEquals(1, page.items().size());
        assertEquals("1001", page.items().get(0).id());
        assertEquals("admin", page.items().get(0).adminUsername());
    }

    @Test
    void getMissingThrowsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        WujiException ex = assertThrows(WujiException.class, () -> service.get("999"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getRejectsNonNumericId() {
        WujiException ex = assertThrows(WujiException.class, () -> service.get("abc"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void buildWhereIncludesFilters() {
        AdminAuditLogRepository repo = new AdminAuditLogRepository(
                org.mockito.Mockito.mock(JdbcTemplate.class), new ObjectMapper());
        AdminAuditLogQuery query = new AdminAuditLogQuery(
                "a_admin", "UPDATE", "llm_config", "llm_primary",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-07T00:00:00Z"),
                1, 20);
        AdminAuditLogRepository.WhereClause where = repo.buildWhere(query);
        assertTrue(where.sql().contains("a.admin_id = ?"));
        assertTrue(where.sql().contains("a.action = ?"));
        assertTrue(where.sql().contains("a.resource_type = ?"));
        assertTrue(where.sql().contains("a.resource_id = ?"));
        assertTrue(where.sql().contains("a.create_time >= ?"));
        assertTrue(where.sql().contains("a.create_time <= ?"));
        assertEquals(6, where.args().size());
    }
}
