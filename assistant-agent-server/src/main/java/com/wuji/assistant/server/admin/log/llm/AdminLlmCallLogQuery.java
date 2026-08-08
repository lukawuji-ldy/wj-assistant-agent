package com.wuji.assistant.server.admin.log.llm;

import java.time.Instant;

/**
 * LLM 调用日志查询条件。
 *
 * @author liudy
 */
public record AdminLlmCallLogQuery(
        String userId,
        String conversationId,
        String messageId,
        String callId,
        String traceId,
        String modelId,
        String provider,
        String status,
        Boolean isFallback,
        Instant createTimeFrom,
        Instant createTimeTo,
        Integer latencyMsMin,
        Integer latencyMsMax,
        Integer promptTokensMin,
        Integer promptTokensMax,
        int page,
        int size
) {
}
