package com.wuji.assistant.server.admin.memory;

import java.util.List;

/**
 * Semantic 更新请求；content 变更会重嵌。
 *
 * @param content    正文
 * @param status     状态
 * @param importance 重要度
 * @param confidence 置信度
 * @param tags       标签；null 不改，空列表清空
 * @author liudy
 */
public record AdminSemanticUpdateRequest(
        String content,
        String status,
        Double importance,
        Double confidence,
        List<String> tags
) {
}
