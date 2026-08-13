package com.wuji.assistant.server.admin.log.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/**
 * LLM 调用日志详情（含 request/response JSON）。
 *
 * @author liudy
 */
public record AdminLlmCallLogDetail(
        String callId,
        String traceId,
        String userId,
        String conversationId,
        String messageId,
        String modelId,
        String provider,
        String bizSource,
        String bizRefId,
        int attempt,
        boolean isFallback,
        String status,
        String errorCode,
        Integer latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        JsonNode requestJson,
        JsonNode responseJson,
        OffsetDateTime createTime
) {
}
