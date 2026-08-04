package com.wuji.assistant.memory.repo;

import com.wuji.assistant.memory.model.UserSemanticHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户语义长期记忆写入与向量检索。
 *
 * @author liudy
 */
@Repository
public class UserSemanticMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(UserSemanticMemoryRepository.class);

    /** 与 schema/13_user_semantic_memory.sql 一致。 */
    public static final int EXPECTED_DIMENSIONS = 1536;

    private static final RowMapper<UserSemanticHit> HIT_MAPPER = (rs, rowNum) -> {
        UserSemanticHit h = new UserSemanticHit();
        h.setId(rs.getString("id"));
        h.setContent(rs.getString("content"));
        h.setConfidence(rs.getFloat("confidence"));
        h.setImportance(rs.getFloat("importance"));
        Timestamp lastUsed = rs.getTimestamp("last_used_time");
        if (lastUsed != null) {
            h.setLastUsedTime(lastUsed.toInstant());
        }
        Timestamp updated = rs.getTimestamp("update_time");
        if (updated != null) {
            h.setUpdateTime(updated.toInstant());
        }
        h.setScore(rs.getDouble("score"));
        return h;
    };

    private final JdbcTemplate jdbcTemplate;

    public UserSemanticMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入 ACTIVE 语义记忆。
     *
     * @param userId            用户
     * @param content           正文
     * @param importance        重要度（可空默认 0.5）
     * @param confidence        置信度（可空默认 0.8）
     * @param sourceMessageId   溯源消息
     * @param vectorLiteral     pgvector 字面量 {@code [..]}
     * @param vectorDimensions  向量维度（须为 1536）
     * @return 新行 id；维度不符或参数非法返回 null
     */
    public String insert(String userId, String content, Double importance, Double confidence,
                         String sourceMessageId, String vectorLiteral, int vectorDimensions) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(content) || !StringUtils.hasText(vectorLiteral)) {
            return null;
        }
        if (vectorDimensions != EXPECTED_DIMENSIONS) {
            log.warn("skip semantic insert: embedding dim={} expected={}", vectorDimensions, EXPECTED_DIMENSIONS);
            return null;
        }
        String id = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.now());
        float imp = importance == null ? 0.5f : importance.floatValue();
        float conf = confidence == null ? 0.8f : confidence.floatValue();
        jdbcTemplate.update("""
                INSERT INTO user_semantic_memory
                (id, user_id, content, memory_type, status, importance, confidence,
                 source, source_message_id, embedding, create_time, update_time)
                VALUES (?::uuid, ?, ?, 'experience', 'ACTIVE', ?, ?, 'EXTRACTED', ?, ?::vector, ?, ?)
                """,
                id, userId, content, imp, conf, sourceMessageId, vectorLiteral, now, now);
        return id;
    }

    /**
     * 余弦近邻检索（必须过滤 user_id + ACTIVE）。
     *
     * @param userId        用户
     * @param vectorLiteral pgvector 字面量
     * @param topK          条数
     * @param minScore      最低余弦分（1 - distance）
     * @return 命中列表
     */
    public List<UserSemanticHit> searchSimilar(String userId, String vectorLiteral, int topK, double minScore) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(vectorLiteral)) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        Timestamp now = Timestamp.from(Instant.now());
        List<UserSemanticHit> hits = jdbcTemplate.query("""
                SELECT id::text AS id, content, confidence, importance,
                       last_used_time, update_time,
                       (1 - (embedding <=> ?::vector)) AS score
                FROM user_semantic_memory
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                  AND (expire_time IS NULL OR expire_time > ?)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, HIT_MAPPER, vectorLiteral, userId, now, vectorLiteral, limit);
        if (hits.isEmpty()) {
            return hits;
        }
        return hits.stream()
                .filter(h -> h.getScore() >= minScore)
                .toList();
    }

    /**
     * 检索命中后更新 last_used_time。
     *
     * @param userId 用户
     * @param ids    行 UUID
     * @return 更新行数
     */
    public int touchLastUsed(String userId, Collection<String> ids) {
        if (!StringUtils.hasText(userId) || ids == null || ids.isEmpty()) {
            return 0;
        }
        List<String> idList = ids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (idList.isEmpty()) {
            return 0;
        }
        Timestamp now = Timestamp.from(Instant.now());
        String placeholders = String.join(",", java.util.Collections.nCopies(idList.size(), "?::uuid"));
        List<Object> args = new ArrayList<>();
        args.add(now);
        args.add(userId);
        args.addAll(idList);
        return jdbcTemplate.update("""
                UPDATE user_semantic_memory
                SET last_used_time = ?
                WHERE user_id = ? AND status = 'ACTIVE' AND id IN (%s)
                """.formatted(placeholders), args.toArray());
    }

    /**
     * 软删：按 id。
     *
     * @param userId   用户
     * @param memoryId 行 UUID
     * @return 更新行数
     */
    public int softDelete(String userId, String memoryId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(memoryId)) {
            return 0;
        }
        return jdbcTemplate.update("""
                UPDATE user_semantic_memory
                SET status = 'DELETED', update_time = ?
                WHERE user_id = ? AND id = ?::uuid AND status = 'ACTIVE'
                """, Timestamp.from(Instant.now()), userId, memoryId);
    }
}
