package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认：仅读 yml {@code wuji.mcp.includeTools}（空 = 全量）。
 *
 * @author liudy
 */
@Component
@ConditionalOnMissingBean(McpAllowlistSource.class)
public class YmlMcpAllowlistSource implements McpAllowlistSource {

    private final WujiMcpProperties mcpProperties;

    public YmlMcpAllowlistSource(WujiMcpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    @Override
    public McpAllowlist resolve(String serverCode) {
        return fromYml(mcpProperties.getIncludeTools());
    }

    /**
     * yml 列表 → 策略。
     *
     * @param includeTools 配置列表
     * @return allowAll 或 only
     */
    public static McpAllowlist fromYml(List<String> includeTools) {
        if (includeTools == null || includeTools.isEmpty()) {
            return McpAllowlist.allowAll();
        }
        Set<String> set = new HashSet<>();
        for (String v : includeTools) {
            if (StringUtils.hasText(v)) {
                set.add(v.trim());
            }
        }
        return set.isEmpty() ? McpAllowlist.allowAll() : McpAllowlist.only(set);
    }
}
