package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DB {@code mcp_tool_binding} 优先（P5.2）：
 * 有任意 ACTIVE {@code mcp_server_ref} 时，某 server 零 binding → 全量；有行 → 仅 enabled=true。
 * 零 ACTIVE server 时回落 yml includeTools。
 *
 * @author liudy
 */
@Component
@Primary
public class DbPreferredMcpAllowlistSource implements McpAllowlistSource {

    private final JdbcTemplate jdbcTemplate;
    private final WujiMcpProperties mcpProperties;

    public DbPreferredMcpAllowlistSource(JdbcTemplate jdbcTemplate, WujiMcpProperties mcpProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.mcpProperties = mcpProperties;
    }

    @Override
    public McpAllowlist resolve(String serverCode) {
        String code = StringUtils.hasText(serverCode) ? serverCode.trim() : DEFAULT_SERVER_CODE;
        Integer activeServers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_server_ref WHERE status = 'ACTIVE'", Integer.class);
        boolean dbConnectionMode = activeServers != null && activeServers > 0;

        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mcp_tool_binding WHERE server_code = ?", Integer.class, code);
        if (cnt == null || cnt == 0) {
            if (dbConnectionMode) {
                return McpAllowlist.allowAll();
            }
            return YmlMcpAllowlistSource.fromYml(mcpProperties.getIncludeTools());
        }
        List<String> enabled = jdbcTemplate.queryForList("""
                SELECT tool_name FROM mcp_tool_binding
                WHERE server_code = ? AND enabled = TRUE
                """, String.class, code);
        Set<String> names = new HashSet<>();
        for (String name : enabled) {
            if (StringUtils.hasText(name)) {
                names.add(name.trim());
            }
        }
        return McpAllowlist.only(names);
    }
}
