package com.wuji.assistant.memory.repo;

import com.wuji.assistant.memory.model.MemoryPage;
import com.wuji.assistant.memory.model.UserSemanticHit;
import com.wuji.assistant.memory.model.UserSemanticMemoryView;
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
import java.util.Locale;
import java.util.Optional;
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

    private static final RowMapper<UserSemanticMemoryView> VIEW_MAPPER = (rs, rowNum) -> {
        UserSemanticMemoryView v = new UserSemanticMemoryView();
        v.setId(rs.getString("id"));
        v.setUserId(rs.getString("user_id"));
        v.setContent(rs.getString("content"));
        v.setMemoryType(rs.getString("memory_type"));
        v.setStatus(rs.getString("status"));
        v.setImportance(rs.getFloat("importance"));
        v.setConfidence(rs.getFloat("confidence"));
        Object tags = rs.getObject("tags");
        if (tags != null) {
            v.setTagsJson(tags.toString());
        }
        v.setSource(rs.getString("source"));
        v.setSourceMessageId(rs.getString("source_message_id"));
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
        try {
            boolean hasScore = false;
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if ("score".equalsIgnoreCase(meta.getColumnLabel(i))) {
                    hasScore = true;
                    break;
                }
            }
            if (hasScore) {
                double score = rs.getDouble("score");
                if (!rs.wasNull()) {
                    v.setScore(score);
                }
            }
        } catch (Exception ignored) {
            // ignore optional score
        }
        return v;
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
     * 管理分页（ILIKE / 状态 / 时间）；相似检索请走 {@link #pageSimilarAdmin}。
     */
    public MemoryPage<UserSemanticMemoryView> pageAdmin(
            String userId,
            String status,
            String keyword,
            Instant createTimeFrom,
            Instant createTimeTo,
            int page,
            int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(userId)) {
            where.append(" AND user_id = ?");
            args.add(userId.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND content ILIKE ?");
            args.add("%" + keyword.trim() + "%");
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
                "SELECT COUNT(*) FROM user_semantic_memory" + where, Long.class, args.toArray());
        long t = total == null ? 0L : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add((p - 1) * s);
        List<UserSemanticMemoryView> items = jdbcTemplate.query("""
                SELECT id::text AS id, user_id, content, memory_type, status, importance, confidence,
                       tags::text AS tags, source, source_message_id, expire_time, last_used_time,
                       create_time, update_time
                FROM user_semantic_memory
                """ + where + """
                 ORDER BY update_time DESC, id ASC
                LIMIT ? OFFSET ?
                """, VIEW_MAPPER, pageArgs.toArray());
        return new MemoryPage<>(items, t, p, s);
    }

    /**
     * 管理相似检索分页（须指定 userId）。
     *
     * @param userId        用户（必填）
     * @param vectorLiteral 查询向量
     * @param minScore      最低分
     * @param page          页
     * @param size          大小
     * @return 分页（total 为过滤后条数的近似：先取 top 再内存分页）
     */
    public MemoryPage<UserSemanticMemoryView> pageSimilarAdmin(
            String userId,
            String vectorLiteral,
            double minScore,
            int page,
            int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(vectorLiteral)) {
            return new MemoryPage<>(List.of(), 0, p, s);
        }
        int fetch = Math.min(p * s + s, 200);
        Timestamp now = Timestamp.from(Instant.now());
        List<UserSemanticMemoryView> hits = jdbcTemplate.query("""
                SELECT id::text AS id, user_id, content, memory_type, status, importance, confidence,
                       tags::text AS tags, source, source_message_id, expire_time, last_used_time,
                       create_time, update_time,
                       (1 - (embedding <=> ?::vector)) AS score
                FROM user_semantic_memory
                WHERE user_id = ?
                  AND status = 'ACTIVE'
                  AND (expire_time IS NULL OR expire_time > ?)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, VIEW_MAPPER, vectorLiteral, userId.trim(), now, vectorLiteral, fetch);
        List<UserSemanticMemoryView> filtered = hits.stream()
                .filter(h -> h.getScore() != null && h.getScore() >= minScore)
                .toList();
        long total = filtered.size();
        int from = Math.min((p - 1) * s, filtered.size());
        int to = Math.min(from + s, filtered.size());
        return new MemoryPage<>(filtered.subList(from, to), total, p, s);
    }

    /**
     * 按 id 查询。
     *
     * @param id UUID
     * @return 视图
     */
    public Optional<UserSemanticMemoryView> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        List<UserSemanticMemoryView> rows = jdbcTemplate.query("""
                SELECT id::text AS id, user_id, content, memory_type, status, importance, confidence,
                       tags::text AS tags, source, source_message_id, expire_time, last_used_time,
                       create_time, update_time
                FROM user_semantic_memory
                WHERE id = ?::uuid
                """, VIEW_MAPPER, id.trim());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 原地更新；content 变更时须同时传新向量。
     *
     * @param id            UUID
     * @param content       正文
     * @param status        状态
     * @param importance    重要度
     * @param confidence    置信度
     * @param tagsJson      标签 JSON 数组，可 null 表示不改；空串清为空数组
     * @param vectorLiteral 新向量；null 表示不改 embedding
     * @return 更新行数
     */
    public int update(
            String id,
            String content,
            String status,
            float importance,
            float confidence,
            String tagsJson,
            String vectorLiteral) {
        if (!StringUtils.hasText(id)) {
            return 0;
        }
        Timestamp now = Timestamp.from(Instant.now());
        if (vectorLiteral != null) {
            if (tagsJson != null) {
                return jdbcTemplate.update("""
                        UPDATE user_semantic_memory
                        SET content = ?, status = ?, importance = ?, confidence = ?,
                            tags = ?::jsonb, embedding = ?::vector, update_time = ?
                        WHERE id = ?::uuid
                        """, content, status, importance, confidence, tagsJson, vectorLiteral, now, id.trim());
            }
            return jdbcTemplate.update("""
                    UPDATE user_semantic_memory
                    SET content = ?, status = ?, importance = ?, confidence = ?,
                        embedding = ?::vector, update_time = ?
                    WHERE id = ?::uuid
                    """, content, status, importance, confidence, vectorLiteral, now, id.trim());
        }
        if (tagsJson != null) {
            return jdbcTemplate.update("""
                    UPDATE user_semantic_memory
                    SET content = ?, status = ?, importance = ?, confidence = ?,
                        tags = ?::jsonb, update_time = ?
                    WHERE id = ?::uuid
                    """, content, status, importance, confidence, tagsJson, now, id.trim());
        }
        return jdbcTemplate.update("""
                UPDATE user_semantic_memory
                SET content = ?, status = ?, importance = ?, confidence = ?, update_time = ?
                WHERE id = ?::uuid
                """, content, status, importance, confidence, now, id.trim());
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
     * 软删：按 user + id。
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

    /**
     * 管理软删：仅按 id。
     *
     * @param memoryId 行 UUID
     * @return 更新行数
     */
    public int softDeleteById(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            return 0;
        }
        return jdbcTemplate.update("""
                UPDATE user_semantic_memory
                SET status = 'DELETED', update_time = ?
                WHERE id = ?::uuid AND status <> 'DELETED'
                """, Timestamp.from(Instant.now()), memoryId.trim());
    }

    /**
     * float[] → pgvector 字面量。
     *
     * @param vector 向量
     * @return {@code [1.0,2.5]}
     */
    public static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
