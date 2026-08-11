package com.wuji.assistant.agent.prompt;

/**
 * 提示词线上副本变更：用于使 ReactAgent 缓存失效（工具 description 烘焙在 Agent 内）。
 *
 * @param code 模板编码；{@code *} 表示全部
 * @author liudy
 */
public record PromptTemplateChangedEvent(String code) {
}
