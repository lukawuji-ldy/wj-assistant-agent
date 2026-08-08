package com.wuji.assistant.server.admin.log.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * llm_call_log 只读查询。
 *
 * @author liudy
 */
@Repository
public class AdminLlmCallLogRepository {

    private static final String SUMMARY_COLS = """
            call_id, trace_id, user_id, conversation_id, message_id, model_id, provider,
            attempt, is_fallback, status, error_code, latency_ms, prompt_tokens, completion_tokens, create_time
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminLlmCallLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<AdminLlmCallLogSummary> summaryMapper = (rs, rowNum) -> new AdminLlmCallLogSummary(
            rs.getString("call_id"),
            rs.getString("trace_id"),
            rs.getString("user_id"),
            rs.getString("conversation_id"),
            rs.getString("message_id"),
            rs.getString("model_id"),
            rs.getString("provider"),
            rs.getInt("attempt"),
            rs.getBoolean("is_fallback"),
            rs.getString("status"),
            rs.getString("error_code"),
            nullableInt(rs, "latency_ms"),
            nullableInt(rs, "prompt_tokens"),
            nullableInt(rs, "completion_tokens"),
            toOffset(rs.getTimestamp("create_time"))
    );

    /**
     * 条件计数。
     */
    public long count(AdminLlmCallLogQuery query) {
        WhereClause where = buildWhere(query);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_call_log" + where.sql(),
                Long.class,
                where.args().toArray());
        return total == null ? 0L : total;
    }

    /**
     * 分页列表（不含 JSON 全文）。
     */
    public List<AdminLlmCallLogSummary> list(AdminLlmCallLogQuery query, int limit, int offset) {
        WhereClause where = buildWhere(query);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                "SELECT " + SUMMARY_COLS + " FROM llm_call_log" + where.sql()
                        + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
                summaryMapper,
                args.toArray());
    }

    /**
     * 按 call_id 查详情。
     */
    public Optional<AdminLlmCallLogDetail> findByCallId(String callId) {
        List<AdminLlmCallLogDetail> rows = jdbcTemplate.query("""
                SELECT call_id, trace_id, user_id, conversation_id, message_id, model_id, provider,
                       attempt, is_fallback, status, error_code, latency_ms, prompt_tokens, completion_tokens,
                       request_json, response_json, create_time
                FROM llm_call_log
                WHERE call_id = ?
                """, (rs, rowNum) -> new AdminLlmCallLogDetail(
                rs.getString("call_id"),
                rs.getString("trace_id"),
                rs.getString("user_id"),
                rs.getString("conversation_id"),
                rs.getString("message_id"),
                rs.getString("model_id"),
                rs.getString("provider"),
                rs.getInt("attempt"),
                rs.getBoolean("is_fallback"),
                rs.getString("status"),
                rs.getString("error_code"),
                nullableInt(rs, "latency_ms"),
                nullableInt(rs, "prompt_tokens"),
                nullableInt(rs, "completion_tokens"),
                parseJson(rs.getObject("request_json")),
                parseJson(rs.getObject("response_json")),
                toOffset(rs.getTimestamp("create_time"))
        ), callId);
        return rows.stream().findFirst();
    }

    WhereClause buildWhere(AdminLlmCallLogQuery query) {
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendEq(sql, args, "user_id", query.userId());
        appendEq(sql, args, "conversation_id", query.conversationId());
        appendEq(sql, args, "message_id", query.messageId());
        appendEq(sql, args, "call_id", query.callId());
        appendEq(sql, args, "trace_id", query.traceId());
        appendEq(sql, args, "model_id", query.modelId());
        appendEq(sql, args, "provider", query.provider());
        appendEq(sql, args, "status", query.status());
        if (query.isFallback() != null) {
            appendClause(sql);
            sql.append("is_fallback = ?");
            args.add(query.isFallback());
        }
        if (query.createTimeFrom() != null) {
            appendClause(sql);
            sql.append("create_time >= ?");
            args.add(Timestamp.from(query.createTimeFrom()));
        }
        if (query.createTimeTo() != null) {
            appendClause(sql);
            sql.append("create_time <= ?");
            args.add(Timestamp.from(query.createTimeTo()));
        }
        if (query.latencyMsMin() != null) {
            appendClause(sql);
            sql.append("latency_ms >= ?");
            args.add(query.latencyMsMin());
        }
        if (query.latencyMsMax() != null) {
            appendClause(sql);
            sql.append("latency_ms <= ?");
            args.add(query.latencyMsMax());
        }
        if (query.promptTokensMin() != null) {
            appendClause(sql);
            sql.append("prompt_tokens >= ?");
            args.add(query.promptTokensMin());
        }
        if (query.promptTokensMax() != null) {
            appendClause(sql);
            sql.append("prompt_tokens <= ?");
            args.add(query.promptTokensMax());
        }
        return new WhereClause(sql.toString(), args);
    }

    private void appendEq(StringBuilder sql, List<Object> args, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        appendClause(sql);
        sql.append(column).append(" = ?");
        args.add(value.trim());
    }

    private static void appendClause(StringBuilder sql) {
        if (sql.isEmpty()) {
            sql.append(" WHERE ");
        } else {
            sql.append(" AND ");
        }
    }

    private JsonNode parseJson(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(raw.toString());
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(raw.toString());
        }
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    /**
     * WHERE 片段。
     */
    record WhereClause(String sql, List<Object> args) {
    }
}
