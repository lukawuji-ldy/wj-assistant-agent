package com.wuji.assistant.agent.checkpoint;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.UUID;

/**
 * {@code agent_checkpoint_link} 写入。查询在管理台。
 *
 * @author liudy
 */
@Repository
public class AgentCheckpointLinkRepository {

    private static final int ERROR_LIMIT = 500;

    private final JdbcTemplate jdbcTemplate;

    public AgentCheckpointLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入一行。调用方负责吞掉失败，避免检索结果因写链接而改变。
     *
     * @param row 链接
     */
    public void insert(AgentCheckpointLinkWrite row) {
        if (row == null || !StringUtils.hasText(row.parentThreadName())
                || !StringUtils.hasText(row.parentToolCallId())
                || !StringUtils.hasText(row.childThreadName())
                || !StringUtils.hasText(row.kind())) {
            return;
        }
        String parentThread = row.parentThreadName().trim();
        String toolCallId = clip(row.parentToolCallId(), 128);
        String childThread = clip(row.childThreadName(), 64);
        String kind = clip(row.kind(), 16);
        String domain = clip(row.domainName(), 128);
        String error = clip(row.errorMessage(), ERROR_LIMIT);
        jdbcTemplate.update("""
                INSERT INTO agent_checkpoint_link (
                    link_id, parent_thread_name, parent_tool_call_id, child_thread_name,
                    kind, domain_name, query_text, rewritten_queries, hit_count, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (PreparedStatement ps) -> {
            ps.setObject(1, UUID.randomUUID(), Types.OTHER);
            ps.setString(2, parentThread);
            ps.setString(3, toolCallId);
            ps.setString(4, childThread);
            ps.setString(5, kind);
            ps.setString(6, domain);
            ps.setString(7, row.queryText());
            ps.setString(8, row.rewrittenQueries());
            if (row.hitCount() == null) {
                ps.setNull(9, Types.INTEGER);
            } else {
                ps.setInt(9, row.hitCount());
            }
            ps.setString(10, error);
        });
    }

    static String clip(String text, int limit) {
        if (!StringUtils.hasText(text) || limit <= 0) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }
}
