package com.wuji.assistant.memory.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * L3 Memory Consolidator：过期标记 + 重要度衰减（不做冷归档删除）。
 *
 * @author liudy
 */
@Service
public class MemoryConsolidator {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidator.class);

    private final JdbcTemplate jdbcTemplate;

    public MemoryConsolidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 整理指定用户；userId 为空则全局扫描。
     *
     * @param userId 用户，可空
     * @return 影响行数（expire + decay）
     */
    public int consolidate(String userId) {
        Timestamp now = Timestamp.from(Instant.now());
        int expired = expireProfiles(userId, now);
        int decayed = decayStaleProfiles(userId, now);
        log.info("memory consolidate userId={} expired={} decayed={}", userId, expired, decayed);
        return expired + decayed;
    }

    int expireProfiles(String userId, Timestamp now) {
        if (userId == null || userId.isBlank()) {
            return jdbcTemplate.update("""
                    UPDATE user_profile
                    SET status = 'EXPIRED', update_time = ?
                    WHERE status = 'ACTIVE' AND expire_time IS NOT NULL AND expire_time < ?
                    """, now, now);
        }
        return jdbcTemplate.update("""
                UPDATE user_profile
                SET status = 'EXPIRED', update_time = ?
                WHERE user_id = ? AND status = 'ACTIVE'
                  AND expire_time IS NOT NULL AND expire_time < ?
                """, now, userId, now);
    }

    int decayStaleProfiles(String userId, Timestamp now) {
        Timestamp staleBefore = Timestamp.from(Instant.now().minus(90, ChronoUnit.DAYS));
        if (userId == null || userId.isBlank()) {
            return jdbcTemplate.update("""
                    UPDATE user_profile
                    SET importance = GREATEST(0, importance - 0.05), update_time = ?
                    WHERE status = 'ACTIVE'
                      AND (last_used_time IS NULL OR last_used_time < ?)
                      AND importance > 0.1
                    """, now, staleBefore);
        }
        return jdbcTemplate.update("""
                UPDATE user_profile
                SET importance = GREATEST(0, importance - 0.05), update_time = ?
                WHERE user_id = ? AND status = 'ACTIVE'
                  AND (last_used_time IS NULL OR last_used_time < ?)
                  AND importance > 0.1
                """, now, userId, staleBefore);
    }
}
