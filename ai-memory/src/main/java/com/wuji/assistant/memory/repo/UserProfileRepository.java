package com.wuji.assistant.memory.repo;

import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.model.MemoryPage;
import com.wuji.assistant.memory.model.UserProfileMemory;
import com.wuji.assistant.memory.model.UserProfileView;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户结构化画像 / 偏好仓储（读路径 + 管理写路径 + last_used 触达）。
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

    private static final RowMapper<UserProfileView> VIEW_MAPPER = (rs, rowNum) -> {
        UserProfileView v = new UserProfileView();
        v.setMemoryId(rs.getString("memory_id"));
        v.setUserId(rs.getString("user_id"));
        v.setMemoryType(rs.getString("memory_type"));
        v.setMemoryKey(rs.getString("memory_key"));
        v.setMemoryValue(rs.getString("memory_value"));
        v.setStatus(rs.getString("status"));
        v.setConfidence(rs.getFloat("confidence"));
        v.setImportance(rs.getFloat("importance"));
        v.setSource(rs.getString("source"));
        v.setVersion(rs.getInt("version"));
        Timestamp expire = rs.getTimestamp("expire_time");
        if (expire != null) {
            v.setExpireTime(expire.toInstant());
        }
        Timestamp lastUsed = rs.getTimestamp("last_used_time");
        if (lastUsed != null) {
            v.setLastUsedTime(lastUsed.toInstant());
        }
        Timestamp created = rs.getTimestamp("create_time");
        if (created != null) {
            v.setCreateTime(created.toInstant());
        }
        Timestamp updated = rs.getTimestamp("update_time");
        if (updated != null) {
            v.setUpdateTime(updated.toInstant());
        }
        return v;
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

    /**
     * 管理分页查询。
     *
     * @param userId         可选用户
     * @param memoryKey      可选 key（精确）
     * @param memoryType     可选类型
     * @param status         可选状态
     * @param createTimeFrom 可选起始（含）
     * @param createTimeTo   可选结束（含）
     * @param page           页码（从 1）
     * @param size           页大小
     * @return 分页
     */
    public MemoryPage<UserProfileView> pageAdmin(
            String userId,
            String memoryKey,
            String memoryType,
            String status,
            Instant createTimeFrom,
            Instant createTimeTo,
            int page,
            int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendEq(where, args, "user_id", userId);
        appendEq(where, args, "memory_key", memoryKey);
        if (StringUtils.hasText(memoryType)) {
            where.append(" AND memory_type = ?");
            args.add(memoryType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (createTimeFrom != null) {
            where.append(" AND create_time >= ?");
            args.add(Timestamp.from(createTimeFrom));
        }
        if (createTimeTo != null) {
            where.append(" AND create_time <= ?");
            args.add(Timestamp.from(createTimeTo));
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_profile" + where, Long.class, args.toArray());
        long t = total == null ? 0L : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add((p - 1) * s);
        List<UserProfileView> items = jdbcTemplate.query("""
                SELECT memory_id, user_id, memory_type, memory_key, memory_value, status,
                       confidence, importance, source, version, expire_time, last_used_time,
                       create_time, update_time
                FROM user_profile
                """ + where + """
                 ORDER BY update_time DESC, memory_id ASC
                LIMIT ? OFFSET ?
                """, VIEW_MAPPER, pageArgs.toArray());
        return new MemoryPage<>(items, t, p, s);
    }

    /**
     * 按 memory_id 查询。
     *
     * @param memoryId 业务键
     * @return 视图
     */
    public Optional<UserProfileView> findByMemoryId(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            return Optional.empty();
        }
        List<UserProfileView> rows = jdbcTemplate.query("""
                SELECT memory_id, user_id, memory_type, memory_key, memory_value, status,
                       confidence, importance, source, version, expire_time, last_used_time,
                       create_time, update_time
                FROM user_profile
                WHERE memory_id = ?
                """, VIEW_MAPPER, memoryId.trim());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 是否存在 ACTIVE (user_id, memory_key)。
     *
     * @param userId    用户
     * @param memoryKey key
     * @param excludeMemoryId 排除的 memory_id（更新时）
     * @return true 若冲突
     */
    public boolean existsActiveKey(String userId, String memoryKey, String excludeMemoryId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(memoryKey)) {
            return false;
        }
        if (StringUtils.hasText(excludeMemoryId)) {
            Integer cnt = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM user_profile
                    WHERE user_id = ? AND memory_key = ? AND status = 'ACTIVE'
                      AND memory_id <> ?
                    """, Integer.class, userId, memoryKey, excludeMemoryId);
            return cnt != null && cnt > 0;
        }
        Integer cnt = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_profile
                WHERE user_id = ? AND memory_key = ? AND status = 'ACTIVE'
                """, Integer.class, userId, memoryKey);
        return cnt != null && cnt > 0;
    }

    /**
     * 插入 ACTIVE profile；调用方须先校验唯一键。
     *
     * @param userId       用户
     * @param memoryType   PROFILE|PREFERENCE
     * @param memoryKey    key
     * @param memoryValue  值
     * @param confidence   置信度
     * @param importance   重要度
     * @param source       来源
     * @return 新建 memory_id
     * @throws DuplicateKeyException 部分唯一索引冲突
     */
    public String insert(
            String userId,
            String memoryType,
            String memoryKey,
            String memoryValue,
            float confidence,
            float importance,
            String source) {
        String memoryId = IdGenerator.nextBizId("mem_");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO user_profile
                (id, memory_id, user_id, memory_type, memory_key, memory_value, status,
                 confidence, importance, source, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, 1, ?, ?)
                """,
                IdGenerator.nextLong(),
                memoryId,
                userId,
                memoryType,
                memoryKey,
                memoryValue,
                confidence,
                importance,
                source,
                now,
                now);
        return memoryId;
    }

    /**
     * 原地 UPDATE（含 version+1）；与运行时 Extract 一致。
     *
     * @param memoryId    业务键
     * @param memoryType  类型
     * @param memoryKey   key
     * @param memoryValue 值
     * @param status      状态
     * @param confidence  置信度
     * @param importance  重要度
     * @return 更新行数
     */
    public int updateByMemoryId(
            String memoryId,
            String memoryType,
            String memoryKey,
            String memoryValue,
            String status,
            float confidence,
            float importance) {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.update("""
                UPDATE user_profile
                SET memory_type = ?, memory_key = ?, memory_value = ?, status = ?,
                    confidence = ?, importance = ?, update_time = ?, version = version + 1
                WHERE memory_id = ?
                """,
                memoryType,
                memoryKey,
                memoryValue,
                status,
                confidence,
                importance,
                now,
                memoryId);
    }

    /**
     * 软删：status=DELETED，version+1。
     *
     * @param memoryId 业务键
     * @return 更新行数
     */
    public int softDeleteByMemoryId(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            return 0;
        }
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.update("""
                UPDATE user_profile
                SET status = 'DELETED', update_time = ?, version = version + 1
                WHERE memory_id = ? AND status <> 'DELETED'
                """, now, memoryId.trim());
    }

    private static void appendEq(StringBuilder where, List<Object> args, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append(" AND ").append(column).append(" = ?");
            args.add(value.trim());
        }
    }
}
