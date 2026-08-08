package com.wuji.assistant.server.admin.log.llm;

import java.util.List;

/**
 * LLM 调用日志分页。
 *
 * @author liudy
 */
public record AdminLlmCallLogPage(
        List<AdminLlmCallLogSummary> items,
        long total,
        int page,
        int size
) {
}
