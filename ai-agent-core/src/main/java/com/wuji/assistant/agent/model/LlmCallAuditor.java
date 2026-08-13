package com.wuji.assistant.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.common.util.PostgresText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 入模审计写入 llm_call_log。
 *
 * @author liudy
 */
@Component
public class LlmCallAuditor {

    private static final Logger log = LoggerFactory.getLogger(LlmCallAuditor.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LlmCallAuditor(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录一次调用。
     *
     * @param params 审计字段
     */
    public void record(AuditParams params) {
        try {
            String requestJson = PostgresText.sanitizeJson(
                    objectMapper.writeValueAsString(sanitizeForJsonb(params.request())));
            String responseJson = params.response() == null
                    ? null
                    : PostgresText.sanitizeJson(objectMapper.writeValueAsString(sanitizeForJsonb(params.response())));
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            jdbcTemplate.update("""
                    INSERT INTO llm_call_log
                    (id, call_id, trace_id, conversation_id, message_id, user_id, biz_source, biz_ref_id,
                     model_id, provider,
                     attempt, is_fallback, status, error_code, latency_ms, prompt_tokens, completion_tokens,
                     request_json, response_json, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                    """,
                    IdGenerator.nextLong(),
                    IdGenerator.nextBizId("call_"),
                    params.traceId(),
                    params.conversationId(),
                    params.messageId(),
                    params.userId(),
                    params.bizSource(),
                    params.bizRefId(),
                    params.modelId(),
                    params.provider(),
                    params.attempt(),
                    params.fallback(),
                    params.status(),
                    params.errorCode(),
                    params.latencyMs(),
                    params.promptTokens(),
                    params.completionTokens(),
                    requestJson,
                    responseJson,
                    Timestamp.from(now.toInstant()));
        } catch (Exception e) {
            log.warn("write llm_call_log failed: {}", e.toString());
        }
    }

    /**
     * 递归剥离字符串中的 NUL，避免 Jackson 产出 {@code \u0000} 后被 PG jsonb 拒绝。
     */
    static Object sanitizeForJsonb(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return PostgresText.sanitize(s);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), sanitizeForJsonb(e.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(sanitizeForJsonb(o));
            }
            return out;
        }
        return value;
    }

    /**
     * 审计入参。
     *
     * @author liudy
     */
    public record AuditParams(
            String traceId,
            String conversationId,
            String messageId,
            String userId,
            String bizSource,
            String bizRefId,
            String modelId,
            String provider,
            int attempt,
            boolean fallback,
            String status,
            String errorCode,
            Integer latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            Map<String, Object> request,
            Map<String, Object> response
    ) {
    }
}
