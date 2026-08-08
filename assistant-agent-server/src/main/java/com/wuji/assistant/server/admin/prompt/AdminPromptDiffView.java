package com.wuji.assistant.server.admin.prompt;

/**
 * 两版提示词 Diff 视图（正文由前端做文本对比）。
 *
 * @param code 模板编码
 * @param from 起始版本
 * @param to   目标版本
 * @author liudy
 */
public record AdminPromptDiffView(
        String code,
        AdminPromptVersionView from,
        AdminPromptVersionView to
) {
}
