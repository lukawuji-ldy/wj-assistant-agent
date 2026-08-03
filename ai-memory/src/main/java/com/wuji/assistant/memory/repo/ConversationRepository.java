package com.wuji.assistant.memory.repo;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.model.Conversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * 会话表仓储（强制按 user_id 隔离）。
 *
 * @author liudy
 */
@Repository
public class ConversationRepository {

    private static final RowMapper<Conversation> MAPPER = (rs, rowNum) -> {
        Conversation c = new Conversation();
        c.setId(rs.getLong("id"));
        c.setConversationId(rs.getString("conversation_id"));
        c.setUserId(rs.getString("user_id"));
        c.setTitle(rs.getString("title"));
        c.setSummary(rs.getString("summary"));
        Timestamp until = rs.getTimestamp("summary_until_time");
        if (until != null) {
            c.setSummaryUntilTime(until.toInstant().atOffset(ZoneOffset.UTC));
        }
        c.setSummaryUntilMessageId(rs.getString("summary_until_message_id"));
        Timestamp compressed = rs.getTimestamp("summary_compressed_at");
        if (compressed != null) {
            c.setSummaryCompressedAt(compressed.toInstant().atOffset(ZoneOffset.UTC));
        }
        c.setMessageCount(rs.getInt("message_count"));
        c.setLastActiveTime(rs.getTimestamp("last_active_time").toInstant().atOffset(ZoneOffset.UTC));
        c.setCreateTime(rs.getTimestamp("create_time").toInstant().atOffset(ZoneOffset.UTC));
        return c;
    };

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 新建空会话。
     *
     * @param userId 用户
     * @param title  标题，可空
     * @return 会话
     */
    public Conversation create(String userId, String title) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Conversation c = new Conversation();
        c.setId(IdGenerator.nextLong());
        c.setConversationId(IdGenerator.nextBizId("c_"));
        c.setUserId(userId);
        c.setTitle(title == null || title.isBlank() ? "新对话" : title);
        c.setMessageCount(0);
        c.setLastActiveTime(now);
        c.setCreateTime(now);
        jdbcTemplate.update("""
                INSERT INTO conversation (id, conversation_id, user_id, title, message_count, last_active_time, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                c.getId(), c.getConversationId(), c.getUserId(), c.getTitle(),
                c.getMessageCount(), Timestamp.from(now.toInstant()), Timestamp.from(now.toInstant()));
        return c;
    }

    /**
     * 按业务键查询并校验归属。
     *
     * @param userId         当前用户
     * @param conversationId 会话业务键
     * @return 会话
     */
    public Conversation requireOwned(String userId, String conversationId) {
        return findOwned(userId, conversationId)
                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "会话不存在或无权访问"));
    }

    /**
     * 查询用户名下会话。
     *
     * @param userId         用户
     * @param conversationId 会话键
     * @return Optional
     */
    public Optional<Conversation> findOwned(String userId, String conversationId) {
        List<Conversation> list = jdbcTemplate.query("""
                SELECT * FROM conversation WHERE conversation_id = ? AND user_id = ?
                """, MAPPER, conversationId, userId);
        return list.stream().findFirst();
    }

    /**
     * 当前用户会话列表（按活跃时间倒序）。
     *
     * @param userId 用户
     * @return 列表
     */
    public List<Conversation> listByUser(String userId) {
        return jdbcTemplate.query("""
                SELECT * FROM conversation WHERE user_id = ? ORDER BY last_active_time DESC
                """, MAPPER, userId);
    }

    /**
     * 更新标题。
     *
     * @param userId         用户
     * @param conversationId 会话
     * @param title          新标题
     * @return 更新后会话
     */
    public Conversation updateTitle(String userId, String conversationId, String title) {
        Conversation c = requireOwned(userId, conversationId);
        jdbcTemplate.update("UPDATE conversation SET title = ? WHERE conversation_id = ? AND user_id = ?",
                title, conversationId, userId);
        c.setTitle(title);
        return c;
    }

    /**
     * 硬删除会话行（调用方须先删消息并校验归属）。
     *
     * @param userId         用户
     * @param conversationId 会话
     * @return 删除行数
     */
    public int deleteOwned(String userId, String conversationId) {
        requireOwned(userId, conversationId);
        return jdbcTemplate.update(
                "DELETE FROM conversation WHERE conversation_id = ? AND user_id = ?",
                conversationId, userId);
    }

    /**
     * 增加消息计数并刷新活跃时间。
     *
     * @param conversationId 会话
     * @param delta          增量
     */
    public void bumpMessageCount(String conversationId, int delta) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE conversation SET message_count = message_count + ?, last_active_time = ?
                WHERE conversation_id = ?
                """, delta, Timestamp.from(now.toInstant()), conversationId);
    }

    /**
     * 原子写入滚动摘要与 watermark。
     *
     * @param conversationId        会话
     * @param summaryJson           摘要 JSON
     * @param untilTime             覆盖截止时间
     * @param untilMessageId        覆盖截止消息
     */
    public void updateSummary(String conversationId, String summaryJson,
                              OffsetDateTime untilTime, String untilMessageId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE conversation
                SET summary = ?, summary_until_time = ?, summary_until_message_id = ?,
                    summary_compressed_at = ?
                WHERE conversation_id = ?
                """,
                summaryJson,
                untilTime == null ? null : Timestamp.from(untilTime.toInstant()),
                untilMessageId,
                Timestamp.from(now.toInstant()),
                conversationId);
    }
}
