package com.wuji.assistant.vta.server.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalysisJobRepositoryTest {

    @Test
    void upsertResult_sanitizesJsonbPayload() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AnalysisJobRepository repo = new AnalysisJobRepository(jdbcTemplate, objectMapper);

        JsonNode node = objectMapper.readTree("{\"k\":\"v\\u0000x\"}");

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Object[] allArgs = inv.getArguments();
                    String customerJson = String.valueOf(allArgs[2]);
                    assertFalse(customerJson.contains("\\u0000"));
                    return 1;
                });

        repo.upsertResult(
                "job_1",
                node,
                node,
                node,
                node,
                node,
                node
        );
    }

    @Test
    void findOwned_emptyWhenUserMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AnalysisJobRepository repo = new AnalysisJobRepository(jdbcTemplate, new ObjectMapper());
        assertTrue(repo.findOwned("job_1", " ").isEmpty());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void findOwned_queriesWithUserId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AnalysisJobRepository repo = new AnalysisJobRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("job_1"), eq("user_a")))
                .thenReturn(List.of());
        assertTrue(repo.findOwned("job_1", "user_a").isEmpty());
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq("job_1"), eq("user_a"));
    }

    @Test
    void findOwnedDetail_joinsResultJson() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AnalysisJobRepository repo = new AnalysisJobRepository(jdbcTemplate, objectMapper);
        Timestamp now = Timestamp.from(Instant.parse("2026-08-13T00:00:00Z"));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("job_1"), eq("user_a")))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("job_id")).thenReturn("job_1");
                    when(rs.getString("product_code")).thenReturn("VTA");
                    when(rs.getString("user_id")).thenReturn("user_a");
                    when(rs.getString("tenant_id")).thenReturn("default");
                    when(rs.getString("input_type")).thenReturn("TEXT");
                    when(rs.getString("transcript_text")).thenReturn("hello");
                    when(rs.getString("status")).thenReturn("SUCCEEDED");
                    when(rs.getString("error_code")).thenReturn(null);
                    when(rs.getString("trace_id")).thenReturn("tr_1");
                    when(rs.getTimestamp("create_time")).thenReturn(now);
                    when(rs.getTimestamp("finish_time")).thenReturn(now);
                    when(rs.getString("customer_tags")).thenReturn("{\"客户标签\":\"a\"}");
                    when(rs.getString("sales_tags")).thenReturn("{\"销售标签\":\"x\"}");
                    when(rs.getString("summary")).thenReturn("{\"总结文本\":\"s\"}");
                    when(rs.getString("intent")).thenReturn("{\"意向度\":\"20\"}");
                    when(rs.getString("aggregate")).thenReturn("{\"aggregateText\":\"ok\"}");
                    Object mapped = mapper.mapRow(rs, 0);
                    return List.of(mapped);
                });

        Optional<AnalysisJobRepository.AnalysisJobDetail> detail = repo.findOwnedDetail("job_1", "user_a");
        assertTrue(detail.isPresent());
        assertEquals("SUCCEEDED", detail.get().job().status());
        assertEquals("a", detail.get().customerTags().path("客户标签").asText());
        assertEquals("ok", detail.get().aggregate().path("aggregateText").asText());
    }

    @Test
    void listOwned_filtersByStatus() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AnalysisJobRepository repo = new AnalysisJobRepository(jdbcTemplate, new ObjectMapper());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user_a"), eq("PARTIAL"), eq(20), eq(0)))
                .thenReturn(List.of());
        repo.listOwned("user_a", 1, 20, "PARTIAL");
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq("user_a"), eq("PARTIAL"), eq(20), eq(0));
    }
}
