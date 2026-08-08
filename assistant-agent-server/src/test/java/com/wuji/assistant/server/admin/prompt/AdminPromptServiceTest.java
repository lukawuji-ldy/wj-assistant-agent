package com.wuji.assistant.server.admin.prompt;

import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.server.admin.audit.AdminAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminPromptService 单元测试。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPromptServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private AdminAuditLogRepository auditLogRepository;

    private AdminPromptService service;
    private final AdminAuthUser admin = AdminAuthUser.of("a_admin", "admin", "SUPER_ADMIN");

    @BeforeEach
    void setUp() {
        service = new AdminPromptService(jdbcTemplate, promptTemplateService, auditLogRepository);
        doAnswer(invocation -> 1).when(jdbcTemplate).update(anyString(), (Object[]) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveDraftCreatesThenPublishInvalidates() {
        when(jdbcTemplate.query(contains("status = 'DRAFT'"), any(RowMapper.class), eq("agent.default.system")))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForObject(contains("MAX(version)"), eq(Integer.class), eq("agent.default.system")))
                .thenReturn(1);
        when(jdbcTemplate.query(contains("FROM prompt_template_version WHERE code = ? AND version = ?"),
                any(RowMapper.class), eq("agent.default.system"), eq(2)))
                .thenAnswer(inv -> List.of(draftView(2)));
        when(jdbcTemplate.query(eq("SELECT published_version FROM prompt_template WHERE code = ?"),
                any(RowMapper.class), eq("agent.default.system")))
                .thenReturn(List.of(1));
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM prompt_template WHERE code = ?"),
                eq(Integer.class), eq("agent.default.system")))
                .thenReturn(1);

        AdminPromptVersionCreateRequest req = new AdminPromptVersionCreateRequest();
        req.setName("v2");
        req.setRole("SYSTEM");
        req.setContent("new content");
        req.setChangeNote("bump");
        req.setPublish(true);

        AdminPromptVersionView view = service.saveDraft(admin, "agent.default.system", req);
        assertEquals(2, view.version());
        assertEquals("PUBLISHED", view.status());
        verify(promptTemplateService).invalidate("agent.default.system");
        ArgumentCaptor<Map<String, ?>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogRepository).insert(eq("a_admin"), eq("CREATE_DRAFT"), eq("PROMPT_TEMPLATE"),
                eq("agent.default.system"), any());
        verify(auditLogRepository).insert(eq("a_admin"), eq("PUBLISH"), eq("PROMPT_TEMPLATE"),
                eq("agent.default.system"), detailCaptor.capture());
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detailCaptor.getValue().get("changes");
        Map<String, Object> publishedChange = changes.stream()
                .filter(c -> "publishedVersion".equals(c.get("field")))
                .findFirst()
                .orElseThrow();
        assertEquals(1, publishedChange.get("from"));
        assertEquals(2, publishedChange.get("to"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rollbackReactivatesTargetVersionNumber() {
        when(jdbcTemplate.query(contains("FROM prompt_template_version WHERE code = ? AND version = ?"),
                any(RowMapper.class), eq("agent.default.system"), eq(1)))
                .thenReturn(List.of(supersededView(1, "old")));
        when(jdbcTemplate.query(eq("SELECT published_version FROM prompt_template WHERE code = ?"),
                any(RowMapper.class), eq("agent.default.system")))
                .thenReturn(List.of(3));
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM prompt_template WHERE code = ?"),
                eq(Integer.class), eq("agent.default.system")))
                .thenReturn(1);

        AdminPromptVersionView view = service.rollback(admin, "agent.default.system", 1);
        assertEquals(1, view.version());
        assertEquals("PUBLISHED", view.status());
        assertEquals("old", view.content());
        assertEquals("note", view.changeNote());
        verify(promptTemplateService).invalidate("agent.default.system");
        ArgumentCaptor<Map<String, ?>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogRepository).insert(eq("a_admin"), eq("ROLLBACK"), eq("PROMPT_TEMPLATE"),
                eq("agent.default.system"), detailCaptor.capture());
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detailCaptor.getValue().get("changes");
        assertEquals("publishedVersion", changes.get(0).get("field"));
        assertEquals(3, changes.get(0).get("from"));
        assertEquals(1, changes.get(0).get("to"));
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) detailCaptor.getValue().get("meta");
        assertEquals(1, meta.get("reactivatedVersion"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listVersionsNotFound() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("missing")))
                .thenReturn(Collections.emptyList());
        WujiException ex = assertThrows(WujiException.class, () -> service.listVersions("missing"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void diffReturnsBothVersions() {
        when(jdbcTemplate.query(contains("FROM prompt_template_version WHERE code = ? AND version = ?"),
                any(RowMapper.class), eq("agent.default.system"), eq(1)))
                .thenReturn(List.of(publishedView(1, "a")));
        when(jdbcTemplate.query(contains("FROM prompt_template_version WHERE code = ? AND version = ?"),
                any(RowMapper.class), eq("agent.default.system"), eq(2)))
                .thenReturn(List.of(publishedView(2, "b")));

        AdminPromptDiffView diff = service.diff("agent.default.system", 1, 2);
        assertEquals("a", diff.from().content());
        assertEquals("b", diff.to().content());
        assertEquals(1, diff.from().version());
        assertEquals(2, diff.to().version());
    }

    private static AdminPromptVersionView draftView(int version) {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        return new AdminPromptVersionView(
                10L, "agent.default.system", "v2", "SYSTEM", "new content", version, "DRAFT",
                "bump", "a_admin", now, null);
    }

    private static AdminPromptVersionView publishedView(int version, String content) {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        return new AdminPromptVersionView(
                version, "agent.default.system", "sys", "SYSTEM", content, version, "PUBLISHED",
                "note", "system", now, now);
    }

    private static AdminPromptVersionView supersededView(int version, String content) {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        return new AdminPromptVersionView(
                version, "agent.default.system", "sys", "SYSTEM", content, version, "SUPERSEDED",
                "note", "system", now, now);
    }
}
