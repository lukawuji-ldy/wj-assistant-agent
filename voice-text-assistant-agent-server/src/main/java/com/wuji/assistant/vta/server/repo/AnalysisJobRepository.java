package com.wuji.assistant.vta.server.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.common.util.PostgresText;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AnalysisJobRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisJobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<AnalysisJobRow> jobRowMapper = (rs, rowNum) -> mapJob(rs);

    public String createPending(String productCode,
                                  String userId,
                                  String tenantId,
                                  String inputType,
                                  String transcriptText) {
        String jobId = IdGenerator.nextBizId("vta_");
        jdbcTemplate.update("""
                INSERT INTO analysis_job
                (job_id, product_code, user_id, tenant_id, input_type, transcript_text, status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                """, jobId, productCode, userId, tenantId, inputType, transcriptText);
        return jobId;
    }

    public Optional<AnalysisJobRow> findOwned(String jobId, String userId) {
        if (!StringUtils.hasText(jobId) || !StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        List<AnalysisJobRow> rows = jdbcTemplate.query("""
                SELECT job_id, product_code, user_id, tenant_id, input_type, transcript_text,
                       status, error_code, trace_id, create_time, finish_time
                FROM analysis_job
                WHERE job_id = ? AND user_id = ?
                """, jobRowMapper, jobId, userId);
        return rows.stream().findFirst();
    }

    public Optional<AnalysisJobDetail> findOwnedDetail(String jobId, String userId) {
        if (!StringUtils.hasText(jobId) || !StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        List<AnalysisJobDetail> rows = jdbcTemplate.query("""
                SELECT j.job_id, j.product_code, j.user_id, j.tenant_id, j.input_type, j.transcript_text,
                       j.status, j.error_code, j.trace_id, j.create_time, j.finish_time,
                       r.customer_tags, r.sales_tags, r.summary, r.intent, r.aggregate
                FROM analysis_job j
                LEFT JOIN analysis_job_result r ON r.job_id = j.job_id
                WHERE j.job_id = ? AND j.user_id = ?
                """, (rs, rowNum) -> new AnalysisJobDetail(
                mapJob(rs),
                readJson(rs.getString("customer_tags")),
                readJson(rs.getString("sales_tags")),
                readJson(rs.getString("summary")),
                readJson(rs.getString("intent")),
                readJson(rs.getString("aggregate"))
        ), jobId, userId);
        return rows.stream().findFirst();
    }

    public List<AnalysisJobSummary> listOwned(String userId, int page, int size) {
        return listOwned(userId, page, size, null);
    }

    public List<AnalysisJobSummary> listOwned(String userId, int page, int size, String status) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(size, 1), 100);
        String sql = """
                SELECT job_id, status, error_code, trace_id, create_time, finish_time
                FROM analysis_job
                WHERE user_id = ?
                """;
        if (StringUtils.hasText(status)) {
            sql += " AND status = ? ";
        }
        sql += """
                ORDER BY create_time DESC
                LIMIT ? OFFSET ?
                """;
        RowMapper<AnalysisJobSummary> mapper = (rs, rowNum) -> new AnalysisJobSummary(
                rs.getString("job_id"),
                rs.getString("status"),
                rs.getString("error_code"),
                rs.getString("trace_id"),
                toOffset(rs.getTimestamp("create_time")),
                rs.getTimestamp("finish_time") == null ? null : toOffset(rs.getTimestamp("finish_time"))
        );
        if (StringUtils.hasText(status)) {
            return jdbcTemplate.query(sql, mapper, userId, status.trim(), s, (p - 1) * s);
        }
        return jdbcTemplate.query(sql, mapper, userId, s, (p - 1) * s);
    }

    public void markRunning(String jobId, String traceId) {
        jdbcTemplate.update("""
                UPDATE analysis_job
                SET status = 'RUNNING',
                    trace_id = ?,
                    finish_time = NULL,
                    error_code = NULL
                WHERE job_id = ?
                """, traceId, jobId);
    }

    public void markSucceeded(String jobId) {
        jdbcTemplate.update("""
                UPDATE analysis_job
                SET status = 'SUCCEEDED',
                    finish_time = NOW(),
                    error_code = NULL
                WHERE job_id = ?
                """, jobId);
    }

    public void markFailed(String jobId, String errorCode) {
        jdbcTemplate.update("""
                UPDATE analysis_job
                SET status = 'FAILED',
                    finish_time = NOW(),
                    error_code = ?
                WHERE job_id = ?
                """, errorCode, jobId);
    }

    public void markPartial(String jobId, String errorCode) {
        jdbcTemplate.update("""
                UPDATE analysis_job
                SET status = 'PARTIAL',
                    finish_time = NOW(),
                    error_code = ?
                WHERE job_id = ?
                """, errorCode, jobId);
    }

    public void upsertResult(String jobId,
                               JsonNode customerTags,
                               JsonNode salesTags,
                               JsonNode summary,
                               JsonNode intentScore,
                               JsonNode aggregate,
                               JsonNode rawNodeOutputs) {
        String customerJson;
        String salesJson;
        String summaryJson;
        String intentJson;
        String aggregateJson;
        String rawJson;
        try {
            customerJson = PostgresText.sanitizeJson(objectMapper.writeValueAsString(customerTags));
            salesJson = PostgresText.sanitizeJson(objectMapper.writeValueAsString(salesTags));
            summaryJson = PostgresText.sanitizeJson(objectMapper.writeValueAsString(summary));
            intentJson = PostgresText.sanitizeJson(objectMapper.writeValueAsString(intentScore));
            aggregateJson = PostgresText.sanitizeJson(objectMapper.writeValueAsString(aggregate));
            rawJson = PostgresText.sanitizeJson(objectMapper.writeValueAsString(rawNodeOutputs));
        } catch (JsonProcessingException e) {
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "analysis result json serialize failed", e);
        }

        jdbcTemplate.update("""
                INSERT INTO analysis_job_result
                (job_id, customer_tags, sales_tags, summary, intent, aggregate, raw_node_outputs)
                VALUES (?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
                ON CONFLICT (job_id) DO UPDATE
                SET customer_tags = EXCLUDED.customer_tags,
                    sales_tags = EXCLUDED.sales_tags,
                    summary = EXCLUDED.summary,
                    intent = EXCLUDED.intent,
                    aggregate = EXCLUDED.aggregate,
                    raw_node_outputs = EXCLUDED.raw_node_outputs,
                    update_time = NOW()
                """,
                jobId, customerJson, salesJson, summaryJson, intentJson, aggregateJson, rawJson);
    }

    private static AnalysisJobRow mapJob(ResultSet rs) throws java.sql.SQLException {
        return new AnalysisJobRow(
                rs.getString("job_id"),
                rs.getString("product_code"),
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                rs.getString("input_type"),
                rs.getString("transcript_text"),
                rs.getString("status"),
                rs.getString("error_code"),
                rs.getString("trace_id"),
                toOffset(rs.getTimestamp("create_time")),
                rs.getTimestamp("finish_time") == null ? null : toOffset(rs.getTimestamp("finish_time"))
        );
    }

    private JsonNode readJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }

    public record AnalysisJobRow(
            String jobId,
            String productCode,
            String userId,
            String tenantId,
            String inputType,
            String transcriptText,
            String status,
            String errorCode,
            String traceId,
            OffsetDateTime createTime,
            OffsetDateTime finishTime) {
    }

    public record AnalysisJobSummary(
            String jobId,
            String status,
            String errorCode,
            String traceId,
            OffsetDateTime createTime,
            OffsetDateTime finishTime) {
    }

    public record AnalysisJobDetail(
            AnalysisJobRow job,
            JsonNode customerTags,
            JsonNode salesTags,
            JsonNode summary,
            JsonNode intent,
            JsonNode aggregate) {
    }
}

