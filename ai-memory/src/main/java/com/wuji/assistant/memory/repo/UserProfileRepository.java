package com.wuji.assistant.memory.repo;

import com.wuji.assistant.memory.model.UserProfileMemory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户结构化画像 / 偏好仓储（读路径 + last_used 触达）。
 *
 * @author liudy
 */
@Repository
public class UserProfileRepository {

    private static final RowMapper<UserProfileMemory> MAPPER = (rs, rowNum) -> {
        UserProfileMemory m = new UserProfileMemory();
        m.setMemoryKey(rs.getString("memory_key"));
        m.setMemoryType(rs.getString("memory_type"));
        m.setMemoryValue(rs.getString("memory_value"));
        m.setConfidence(rs.getFloat("confidence"));
        m.setImportance(rs.getFloat("importance"));
        Timestamp lastUsed = rs.getTimestamp("last_used_time");
        if (lastUsed != null) {
            m.setLastUsedTime(lastUsed.toInstant());
        }
        Timestamp updated = rs.getTimestamp("update_time");
        if (updated != null) {
            m.setUpdateTime(updated.toInstant());
        }
        return m;
    };

    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 列出 ACTIVE 且未过期的画像 / 偏好。
     *
     * @param userId 用户
     * @param types  memory_type 集合（PROFILE / PREFERENCE）；空则全类型
     * @return 行列表（无则空）
     */
    public List<UserProfileMemory> listActive(String userId, Set<String> types) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        Timestamp now = Timestamp.from(Instant.now());
        if (types == null || types.isEmpty()) {
            return jdbcTemplate.query("""
                    SELECT memory_key, memory_type, memory_value, confidence, importance,
                           last_used_time, update_time
                    FROM user_profile
                    WHERE user_id = ? AND status = 'ACTIVE'
                      AND (expire_time IS NULL OR expire_time > ?)
                    """, MAPPER, userId, now);
        }
        List<String> normalized = types.stream()
                .filter(StringUtils::hasText)
                .map(t -> t.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(normalized.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(now);
        args.addAll(normalized);
        return jdbcTemplate.query("""
                SELECT memory_key, memory_type, memory_value, confidence, importance,
                       last_used_time, update_time
                FROM user_profile
                WHERE user_id = ? AND status = 'ACTIVE'
                  AND (expire_time IS NULL OR expire_time > ?)
                  AND memory_type IN (%s)
                """.formatted(placeholders), MAPPER, args.toArray());
    }

    /**
     * 检索命中后更新 last_used_time。
     *
     * @param userId     用户
     * @param memoryKeys 命中 key
     * @return 更新行数
     */
    public int touchLastUsed(String userId, Collection<String> memoryKeys) {
        if (!StringUtils.hasText(userId) || memoryKeys == null || memoryKeys.isEmpty()) {
            return 0;
        }
        List<String> keys = memoryKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (keys.isEmpty()) {
            return 0;
        }
        Timestamp now = Timestamp.from(Instant.now());
        String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(now);
        args.add(userId);
        args.addAll(keys);
        return jdbcTemplate.update("""
                UPDATE user_profile
                SET last_used_time = ?
                WHERE user_id = ? AND status = 'ACTIVE' AND memory_key IN (%s)
                """.formatted(placeholders), args.toArray());
    }
}
