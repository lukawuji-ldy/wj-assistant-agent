package com.wuji.assistant.agent.tool;

import java.util.Set;

/**
 * MCP 工具允许名单策略。
 *
 * @param restrict     true 时仅注入 {@code allowedNames}（可为空 = 全部禁用）
 * @param allowedNames 允许的工具名；{@code restrict=false} 时忽略
 * @author liudy
 */
public record McpAllowlist(boolean restrict, Set<String> allowedNames) {

    /**
     * 不限制：注入发现到的全部工具。
     */
    public static McpAllowlist allowAll() {
        return new McpAllowlist(false, Set.of());
    }

    /**
     * 仅允许给定名称（空集合 = 一个都不注入）。
     *
     * @param names 工具名
     * @return 限制策略
     */
    public static McpAllowlist only(Set<String> names) {
        return new McpAllowlist(true, names == null ? Set.of() : Set.copyOf(names));
    }
}
