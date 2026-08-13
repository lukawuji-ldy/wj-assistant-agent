package com.wuji.assistant.server.admin.log.llm;

import java.time.OffsetDateTime;

/**
 * LLM 调用日志列表行（不含 request/response 全文）。
 *
 * @author liudy
 */
public record AdminLlmCallLogSummary(
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
        OffsetDateTime createTime
) {
}
