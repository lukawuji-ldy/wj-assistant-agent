package com.wuji.assistant.memory.repo;

import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.common.token.TokenEstimator;
import com.wuji.assistant.memory.model.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

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
     * 更新消息正文与状态。token 按主模型词表计算。
     *
     * @param messageId 消息业务键
     * @param content   正文
     * @param status    状态
     */
    public void updateContentAndStatus(String messageId, String content, String status) {
        updateContentAndStatus(messageId, content, status, null);
    }

    /**
     * 更新消息正文与状态，并按指定模型配置计算 token。
     *
     * @param messageId 消息业务键
     * @param content   正文
     * @param status    状态
     * @param configId  实际应答的 llm_config.config_id；空则用主模型
     */
    public void updateContentAndStatus(String messageId, String content, String status, String configId) {
        String safeContent = sanitizeForPostgres(content);
        int tokens = StringUtils.hasText(configId)
                ? TokenEstimator.estimate(safeContent, configId)
                : TokenEstimator.estimate(safeContent);
        jdbcTemplate.update("""
                UPDATE chat_message SET content = ?, token_count = ?, status = ? WHERE message_id = ?
                """, safeContent, tokens, status, messageId);
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
     * watermark 边界片段。有 {@code untilMessageId} 时先按时间、同毫秒再用 message_id 消歧，
     * 与 {@code conversation.summary_until_message_id} 对齐；为空时退化为纯时间边界。
     */
    private static String watermarkClause(String untilMessageId) {
        return StringUtils.hasText(untilMessageId)
                ? "(create_time > ? OR (create_time = ? AND id > (SELECT id FROM chat_message WHERE message_id = ?)))"
                : "create_time > ?";
    }

    private static List<Object> boundaryArgs(String conversationId, String userId,
                                             OffsetDateTime untilTime, String untilMessageId) {
        List<Object> args = new ArrayList<>();
        args.add(conversationId);
        args.add(userId);
        args.add(Timestamp.from(untilTime.toInstant()));
        if (StringUtils.hasText(untilMessageId)) {
            args.add(Timestamp.from(untilTime.toInstant()));
            args.add(untilMessageId);
        }
        return args;
    }

    private static Object[] watermarkArgs(String conversationId, String userId,
                                          OffsetDateTime untilTime, String untilMessageId, Object tail) {
        List<Object> args = boundaryArgs(conversationId, userId, untilTime, untilMessageId);
        args.add(tail);
        return args.toArray();
    }

    /**
     * 拉取 watermark 之后的最近消息（时间正序）。
     * <p>
     * 短期窗口用：{@code ORDER BY create_time DESC LIMIT} 再反转，得到最新 N 条。
     * 摘要压缩不要用本方法，改用 {@link #listOldestAfterWatermarkAsc}。
     *
     * @param conversationId  会话
     * @param userId          用户
     * @param untilTime       summary_until_time
     * @param untilMessageId  summary_until_message_id，可空
     * @param limit           窗口上限
     * @return 正序消息（最新一段）
     */
    public List<ChatMessage> listAfterWatermarkAsc(String conversationId, String userId,
                                                  OffsetDateTime untilTime, String untilMessageId, int limit) {
        if (untilTime == null) {
            return listRecentAsc(conversationId, userId, limit);
        }
        String sql = """
                SELECT * FROM chat_message
                WHERE conversation_id = ? AND user_id = ?
                  AND %s
                ORDER BY create_time DESC, id DESC
                LIMIT ?
                """.formatted(watermarkClause(untilMessageId));
        List<ChatMessage> desc = jdbcTemplate.query(sql, MAPPER,
                watermarkArgs(conversationId, userId, untilTime, untilMessageId, limit));
        List<ChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /**
     * 拉取 watermark 之后最早的一段消息（时间正序）。
     * <p>
     * 摘要压缩用：每次取紧挨旧 watermark 的连续段，而不是最新 N 条。
     * {@code untilTime == null} 时不加边界条件，取全会话最旧的 {@code limit} 条。
     *
     * @param conversationId  会话
     * @param userId          用户
     * @param untilTime       summary_until_time，可空
     * @param untilMessageId  summary_until_message_id，可空
     * @param limit           条数上限
     * @return 正序消息（最旧一段）
     */
    public List<ChatMessage> listOldestAfterWatermarkAsc(String conversationId, String userId,
                                                         OffsetDateTime untilTime, String untilMessageId, int limit) {
        if (untilTime == null) {
            return jdbcTemplate.query("""
                    SELECT * FROM chat_message
                    WHERE conversation_id = ? AND user_id = ?
                    ORDER BY create_time ASC, id ASC
                    LIMIT ?
                    """, MAPPER, conversationId, userId, limit);
        }
        String sql = """
                SELECT * FROM chat_message
                WHERE conversation_id = ? AND user_id = ?
                  AND %s
                ORDER BY create_time ASC, id ASC
                LIMIT ?
                """.formatted(watermarkClause(untilMessageId));
        return jdbcTemplate.query(sql, MAPPER,
                watermarkArgs(conversationId, userId, untilTime, untilMessageId, limit));
    }

    /**
     * watermark 之后消息的 token_count 合计。{@code untilTime == null} 时统计该会话全部消息。
     *
     * @param conversationId  会话
     * @param userId          用户
     * @param untilTime       summary_until_time，可空
     * @param untilMessageId  summary_until_message_id，可空
     * @return token 合计
     */
    public long sumTokenCountAfterWatermark(String conversationId, String userId,
                                            OffsetDateTime untilTime, String untilMessageId) {
        Long sum;
        if (untilTime == null) {
            sum = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(SUM(token_count), 0) FROM chat_message
                    WHERE conversation_id = ? AND user_id = ?
                    """, Long.class, conversationId, userId);
        } else {
            String sql = """
                    SELECT COALESCE(SUM(token_count), 0) FROM chat_message
                    WHERE conversation_id = ? AND user_id = ?
                      AND %s
                    """.formatted(watermarkClause(untilMessageId));
            sum = jdbcTemplate.queryForObject(sql, Long.class,
                    boundaryArgs(conversationId, userId, untilTime, untilMessageId).toArray());
        }
        return sum == null ? 0L : sum;
    }

    /**
     * watermark 之后消息条数。{@code untilTime == null} 时统计该会话全部消息。
     * 用于摘要压缩触发判定，避免使用终身累计的 {@code conversation.message_count}。
     *
     * @param conversationId  会话
     * @param userId          用户
     * @param untilTime       summary_until_time，可空
     * @param untilMessageId  summary_until_message_id，可空
     * @return 条数
     */
    public long countAfterWatermark(String conversationId, String userId,
                                    OffsetDateTime untilTime, String untilMessageId) {
        Long count;
        if (untilTime == null) {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM chat_message
                    WHERE conversation_id = ? AND user_id = ?
                    """, Long.class, conversationId, userId);
        } else {
            String sql = """
                    SELECT COUNT(*) FROM chat_message
                    WHERE conversation_id = ? AND user_id = ?
                      AND %s
                    """.formatted(watermarkClause(untilMessageId));
            count = jdbcTemplate.queryForObject(sql, Long.class,
                    boundaryArgs(conversationId, userId, untilTime, untilMessageId).toArray());
        }
        return count == null ? 0L : count;
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

    /**
     * 用户消息与未指定模型时的 token_count：主模型词表。
     * 助手消息在应答落定时用 {@link TokenEstimator#estimate(String, String)}。
     * 费用与真实上下文占用以 {@code llm_call_log} 中的 API usage 为准。
     */
    private static int estimateTokens(String content) {
        return TokenEstimator.estimate(content);
    }
}
