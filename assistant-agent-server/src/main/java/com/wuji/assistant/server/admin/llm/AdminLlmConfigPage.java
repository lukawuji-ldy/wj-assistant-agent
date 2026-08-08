package com.wuji.assistant.server.admin.llm;

import java.util.List;

/**
 * LLM 配置分页。
 *
 * @param items 行
 * @param total 总数
 * @param page  页码
 * @param size  页大小
 * @author liudy
 */
public record AdminLlmConfigPage(
        List<AdminLlmConfigView> items,
        long total,
        int page,
        int size
) {
}
