package com.wuji.assistant.server.admin.log.llm;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 管理台 LLM 调用日志只读查询。
 *
 * @author liudy
 */
@Service
public class AdminLlmCallLogService {

    private final AdminLlmCallLogRepository repository;

    public AdminLlmCallLogService(AdminLlmCallLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页列表。
     */
    public AdminLlmCallLogPage list(
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
            int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        if (createTimeFrom != null && createTimeTo != null && createTimeFrom.isAfter(createTimeTo)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "createTimeFrom 不能晚于 createTimeTo");
        }
        AdminLlmCallLogQuery query = new AdminLlmCallLogQuery(
                blankToNull(userId),
                blankToNull(conversationId),
                blankToNull(messageId),
                blankToNull(callId),
                blankToNull(traceId),
                blankToNull(modelId),
                blankToNull(provider),
                blankToNull(status),
                isFallback,
                createTimeFrom,
                createTimeTo,
                latencyMsMin,
                latencyMsMax,
                promptTokensMin,
                promptTokensMax,
                p,
                s);
        long total = repository.count(query);
        return new AdminLlmCallLogPage(repository.list(query, s, (p - 1) * s), total, p, s);
    }

    /**
     * 详情。
     */
    public AdminLlmCallLogDetail get(String callId) {
        if (!StringUtils.hasText(callId)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "callId 不能为空");
        }
        return repository.findByCallId(callId.trim())
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "调用日志不存在: " + callId));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
