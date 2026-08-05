package com.wuji.assistant.memory.repo;

import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.model.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天消息仓储。
 *
 * @author liudy
 */
@Repository
public class ChatMessageRepository {

    private static final RowMapper<ChatMessage> MAPPER = (rs, rowNum) -> {
        ChatMessage m = new ChatMessage();
        m.setId(rs.getLong("id"));
        m.setMessageId(rs.getString("message_id"));
        m.setConversationId(rs.getString("conversation_id"));
        m.setUserId(rs.getString("user_id"));
        m.setRole(rs.getString("role"));
        m.setContent(rs.getString("content"));
        m.setTokenCount(rs.getInt("token_count"));
        m.setStatus(rs.getString("status"));
        m.setCreateTime(rs.getTimestamp("create_time").toInstant().atOffset(ZoneOffset.UTC));
        return m;
    };

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入消息。
     *
     * @param conversationId 会话
     * @param userId         用户
     * @param role           角色
     * @param content        正文
     * @param status         状态
     * @return 消息
     */
    public ChatMessage insert(String conversationId, String userId, String role, String content, String status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String safeContent = sanitizeForPostgres(content);
        ChatMessage m = new ChatMessage();
        m.setId(IdGenerator.nextLong());
        m.setMessageId(IdGenerator.nextBizId("m_"));
        m.setConversationId(conversationId);
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(safeContent);
        m.setTokenCount(estimateTokens(safeContent));
        m.setStatus(status);
        m.setCreateTime(now);
        jdbcTemplate.update("""
                INSERT INTO chat_message
                (id, message_id, conversation_id, user_id, role, content, token_count, status, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                m.getId(), m.getMessageId(), m.getConversationId(), m.getUserId(), m.getRole(),
                m.getContent(), m.getTokenCount(), m.getStatus(), Timestamp.from(now.toInstant()));
        return m;
    }

    /**
     * 更新消息正文与状态。
     *
     * @param messageId 消息业务键
     * @param content   正文
     * @param status    状态
     */
    public void updateContentAndStatus(String messageId, String content, String status) {
        String safeContent = sanitizeForPostgres(content);
        jdbcTemplate.update("""
                UPDATE chat_message SET content = ?, token_count = ?, status = ? WHERE message_id = ?
                """, safeContent, estimateTokens(safeContent), status, messageId);
    }

    /**
     * PostgreSQL text/varchar 拒绝 UTF-8 NUL（0x00）。部分兼容模型流式 chunk 会夹带该字节，
     * 若不剥离会导致 UPDATE 失败、status 永久停在 STREAMING。
     *
     * @param content 原始正文，可空
     * @return 可安全写入 PG 的正文（null → 空串）
     */
    static String sanitizeForPostgres(String content) {
        String sanitized = com.wuji.assistant.common.util.PostgresText.sanitize(content);
        return sanitized == null ? "" : sanitized;
    }

    /**
     * 按会话拉取最近消息（时间正序），用于短期上下文。
     *
     * @param conversationId 会话
     * @param userId         用户
     * @param limit          条数上限
     * @return 正序消息
     */
    public List<ChatMessage> listRecentAsc(String conversationId, String userId, int limit) {
        List<ChatMessage> desc = jdbcTemplate.query("""
                SELECT * FROM chat_message
                WHERE conversation_id = ? AND user_id = ?
                ORDER BY create_time DESC, id DESC
                LIMIT ?
                """, MAPPER, conversationId, userId, limit);
        List<ChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /**
     * 拉取 watermark 之后的消息（正序），再按 limit 截取最近段。
     *
     * @param conversationId 会话
     * @param userId         用户
     * @param untilTime      summary_until_time
     * @param limit          窗口上限
     * @return 正序消息
     */
    public List<ChatMessage> listAfterWatermarkAsc(String conversationId, String userId,
                                                  OffsetDateTime untilTime, int limit) {
        if (untilTime == null) {
            return listRecentAsc(conversationId, userId, limit);
        }
        List<ChatMessage> desc = jdbcTemplate.query("""
                SELECT * FROM chat_message
                WHERE conversation_id = ? AND user_id = ?
                  AND create_time > ?
                ORDER BY create_time DESC, id DESC
                LIMIT ?
                """, MAPPER, conversationId, userId, Timestamp.from(untilTime.toInstant()), limit);
        List<ChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /**
     * 分页拉取会话消息（正序）。
     *
     * @param conversationId 会话
     * @param userId         用户
     * @param limit          条数
     * @param offset         偏移
     * @return 消息列表
     */
    public List<ChatMessage> listPageAsc(String conversationId, String userId, int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT * FROM chat_message
                WHERE conversation_id = ? AND user_id = ?
                ORDER BY create_time ASC, id ASC
                LIMIT ? OFFSET ?
                """, MAPPER, conversationId, userId, limit, offset);
    }

    /**
     * 硬删除会话下全部消息（按 user_id 隔离）。
     *
     * @param conversationId 会话
     * @param userId         用户
     * @return 删除行数
     */
    public int deleteByConversation(String conversationId, String userId) {
        return jdbcTemplate.update("""
                DELETE FROM chat_message WHERE conversation_id = ? AND user_id = ?
                """, conversationId, userId);
    }

    private static int estimateTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return Math.max(1, content.length() / 2);
    }
}
