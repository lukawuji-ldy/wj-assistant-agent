package com.wuji.assistant.memory.extract;

import com.wuji.assistant.common.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L2 Memory Extract：规则抽取 + 幂等落库（禁止整段对话入库）。
 *
 * @author liudy
 */
@Service
public class MemoryExtractService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractService.class);

    private static final Pattern NAME_PATTERN = Pattern.compile("我(?:叫|是)\\s*([\\u4e00-\\u9fa5A-Za-z0-9_]{1,32})");
    private static final Pattern PREF_PATTERN = Pattern.compile("我(?:喜欢|偏好|爱)\\s*(.+)");

    private final JdbcTemplate jdbcTemplate;

    public MemoryExtractService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 异步语义入口：由调用方在 boundedElastic 触发。
     *
     * @param conversationId 会话
     * @param messageId      助手消息 id（幂等键）
     * @param userId         用户
     * @param userText       用户原文
     * @param assistantText  助手原文（仅作上下文，不整段入库）
     */
    public void extractAsync(String conversationId, String messageId, String userId,
                             String userText, String assistantText) {
        try {
            extract(conversationId, messageId, userId, userText, assistantText);
        } catch (Exception e) {
            log.warn("memory extract failed messageId={}: {}", messageId, e.toString());
        }
    }

    /**
     * 同步提取（单测 / 显式调用）。
     */
    public MemoryActionItem extract(String conversationId, String messageId, String userId,
                                    String userText, String assistantText) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(userId)) {
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_extract_log WHERE message_id = ?", Integer.class, messageId);
        if (exists != null && exists > 0) {
            log.debug("memory extract skip duplicate messageId={}", messageId);
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }

        MemoryActionItem item = decide(userText);
        Timestamp now = Timestamp.from(Instant.now());
        try {
            if (item.action() != MemoryAction.IGNORE
                    && ("PROFILE".equals(item.resultType()) || "PREFERENCE".equals(item.resultType()))) {
                upsertProfile(userId, item, now);
            }
            insertLog(conversationId, messageId, userId, "SUCCESS", item, null, now);
        } catch (Exception e) {
            insertLog(conversationId, messageId, userId, "FAILED", item, truncate(e.getMessage()), now);
            throw e;
        }
        return item;
    }

    public MemoryActionItem decide(String userText) {
        if (!StringUtils.hasText(userText)) {
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }
        String text = userText.trim();
        Matcher name = NAME_PATTERN.matcher(text);
        if (name.find()) {
            return new MemoryActionItem(MemoryAction.UPDATE, "PROFILE", "display_name", name.group(1));
        }
        Matcher pref = PREF_PATTERN.matcher(text);
        if (pref.find()) {
            String value = pref.group(1).trim();
            if (value.length() > 200) {
                value = value.substring(0, 200);
            }
            return new MemoryActionItem(MemoryAction.UPDATE, "PREFERENCE", "preference.note", value);
        }
        // 显式「记住」由 L1 rememberExplicit 处理；此处不再 IGNORE 后丢弃——若 L2 单独跑到则写入 note
        String explicit = ExplicitRememberDetector.detectContent(text);
        if (explicit != null) {
            return new MemoryActionItem(MemoryAction.UPDATE, "PREFERENCE", "preference.explicit", explicit);
        }
        return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
    }

    /**
     * L1：用户显式记忆指令，同步写入（source=USER_DIRECT）。
     *
     * @param conversationId 会话
     * @param messageId      用户消息 id（幂等）
     * @param userId         用户
     * @param utterance      原文
     * @return action
     */
    public MemoryActionItem rememberExplicit(String conversationId, String messageId,
                                             String userId, String utterance) {
        String content = ExplicitRememberDetector.detectContent(utterance);
        if (content == null || !StringUtils.hasText(userId) || !StringUtils.hasText(messageId)) {
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memory_extract_log WHERE message_id = ?", Integer.class, messageId);
        if (exists != null && exists > 0) {
            return new MemoryActionItem(MemoryAction.IGNORE, "NONE", null, null);
        }
        MemoryActionItem item = new MemoryActionItem(
                MemoryAction.UPDATE, "PREFERENCE", "preference.explicit", content);
        Timestamp now = Timestamp.from(Instant.now());
        try {
            upsertProfile(userId, item, now, "USER_DIRECT");
            insertLog(conversationId, messageId, userId, "SUCCESS", item, null, now);
        } catch (Exception e) {
            insertLog(conversationId, messageId, userId, "FAILED", item, truncate(e.getMessage()), now);
            throw e;
        }
        return item;
    }

    private void upsertProfile(String userId, MemoryActionItem item, Timestamp now) {
        upsertProfile(userId, item, now, "extract");
    }

    private void upsertProfile(String userId, MemoryActionItem item, Timestamp now, String source) {
        Integer cnt = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_profile
                WHERE user_id = ? AND memory_key = ? AND status = 'ACTIVE'
                """, Integer.class, userId, item.memoryKey());
        if (cnt != null && cnt > 0) {
            jdbcTemplate.update("""
                    UPDATE user_profile
                    SET memory_value = ?, update_time = ?, version = version + 1, source = ?
                    WHERE user_id = ? AND memory_key = ? AND status = 'ACTIVE'
                    """, item.memoryValue(), now, source, userId, item.memoryKey());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO user_profile
                    (id, memory_id, user_id, memory_type, memory_key, memory_value, status,
                     confidence, importance, source, version, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 0.90, 0.80, ?, 1, ?, ?)
                    """,
                    IdGenerator.nextLong(),
                    IdGenerator.nextBizId("mem_"),
                    userId,
                    item.resultType(),
                    item.memoryKey(),
                    item.memoryValue(),
                    source,
                    now, now);
        }
    }

    private void insertLog(String conversationId, String messageId, String userId, String status,
                           MemoryActionItem item, String error, Timestamp now) {
        jdbcTemplate.update("""
                INSERT INTO memory_extract_log
                (id, user_id, conversation_id, message_id, status, action, result_type,
                 error_message, retry_count, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                IdGenerator.nextLong(),
                userId,
                conversationId,
                messageId,
                status,
                item.action().name(),
                item.resultType(),
                error,
                now, now);
    }

    private static String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 500);
    }
}
