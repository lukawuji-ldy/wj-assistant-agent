package com.wuji.assistant.server.admin.log.checkpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GraphThread / GraphCheckpoint 只读查询。
 * <p>
 * Flyway / schema DDL 未加引号建表，PG 折叠为小写 {@code graphthread} / {@code graphcheckpoint}；
 * 禁止写 {@code "GraphThread"}，否则 relation does not exist。
 *
 * @author liudy
 */
@Repository
public class AdminCheckpointRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminCheckpointRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<RawStep> stepMapper = (rs, rowNum) -> new RawStep(
            rs.getObject("checkpoint_id").toString(),
            rs.getObject("parent_checkpoint_id") == null ? null : rs.getObject("parent_checkpoint_id").toString(),
            rs.getString("node_id"),
            rs.getString("next_node_id"),
            toOffset(rs.getTimestamp("saved_at")),
            rs.getString("state_content_type")
    );

    /**
     * 线程条件计数。
     */
    public long countThreads(String threadName, Boolean isReleased, Instant savedFrom, Instant savedTo) {
        WhereClause where = buildThreadWhere(threadName, isReleased, savedFrom, savedTo);
        Long total = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM graphthread t
                        """ + where.sql(),
                Long.class,
                where.args().toArray());
        return total == null ? 0L : total;
    }

    /**
     * 线程分页（含 checkpoint 数与最近 saved_at）。
     */
    public List<AdminCheckpointThreadSummary> listThreads(
            String threadName, Boolean isReleased, Instant savedFrom, Instant savedTo, int limit, int offset) {
        WhereClause where = buildThreadWhere(threadName, isReleased, savedFrom, savedTo);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query("""
                SELECT t.thread_id, t.thread_name, t.is_released,
                       COALESCE(c.cnt, 0) AS checkpoint_count,
                       c.last_saved_at
                FROM graphthread t
                LEFT JOIN (
                    SELECT thread_id, COUNT(*) AS cnt, MAX(saved_at) AS last_saved_at
                    FROM graphcheckpoint
                    GROUP BY thread_id
                ) c ON c.thread_id = t.thread_id
                """ + where.sql() + """
                 ORDER BY c.last_saved_at DESC NULLS LAST, t.thread_id
                 LIMIT ? OFFSET ?
                """, (rs, rowNum) -> {
            String name = rs.getString("thread_name");
            ThreadNameParts parts = parseThreadName(name);
            return new AdminCheckpointThreadSummary(
                    rs.getObject("thread_id").toString(),
                    name,
                    parts.userId(),
                    parts.conversationId(),
                    rs.getBoolean("is_released"),
                    rs.getLong("checkpoint_count"),
                    toOffset(rs.getTimestamp("last_saved_at")));
        }, args.toArray());
    }

    /**
     * 按 thread_id 查线程头。
     */
    public Optional<AdminCheckpointThreadSummary> findThread(UUID threadId) {
        List<AdminCheckpointThreadSummary> rows = jdbcTemplate.query("""
                SELECT t.thread_id, t.thread_name, t.is_released,
                       COALESCE(c.cnt, 0) AS checkpoint_count,
                       c.last_saved_at
                FROM graphthread t
                LEFT JOIN (
                    SELECT thread_id, COUNT(*) AS cnt, MAX(saved_at) AS last_saved_at
                    FROM graphcheckpoint
                    GROUP BY thread_id
                ) c ON c.thread_id = t.thread_id
                WHERE t.thread_id = ?
                """, (rs, rowNum) -> {
            String name = rs.getString("thread_name");
            ThreadNameParts parts = parseThreadName(name);
            return new AdminCheckpointThreadSummary(
                    rs.getObject("thread_id").toString(),
                    name,
                    parts.userId(),
                    parts.conversationId(),
                    rs.getBoolean("is_released"),
                    rs.getLong("checkpoint_count"),
                    toOffset(rs.getTimestamp("last_saved_at")));
        }, threadId);
        return rows.stream().findFirst();
    }

    /**
     * 加载线程下全部步骤（无 state）。
     */
    public List<RawStep> listRawSteps(UUID threadId) {
        return jdbcTemplate.query("""
                SELECT checkpoint_id, parent_checkpoint_id, node_id, next_node_id, saved_at, state_content_type
                FROM graphcheckpoint
                WHERE thread_id = ?
                ORDER BY saved_at ASC
                """, stepMapper, threadId);
    }

    /**
     * 单步全量（原始 state_data）。
     */
    public Optional<AdminCheckpointRaw> findCheckpoint(UUID checkpointId) {
        List<AdminCheckpointRaw> rows = jdbcTemplate.query("""
                SELECT checkpoint_id, parent_checkpoint_id, thread_id, node_id, next_node_id,
                       saved_at, state_content_type, state_data
                FROM graphcheckpoint
                WHERE checkpoint_id = ?
                """, (rs, rowNum) -> new AdminCheckpointRaw(
                rs.getObject("checkpoint_id").toString(),
                rs.getObject("parent_checkpoint_id") == null ? null : rs.getObject("parent_checkpoint_id").toString(),
                rs.getObject("thread_id").toString(),
                rs.getString("node_id"),
                rs.getString("next_node_id"),
                toOffset(rs.getTimestamp("saved_at")),
                rs.getString("state_content_type"),
                parseJson(rs.getObject("state_data"))
        ), checkpointId);
        return rows.stream().findFirst();
    }

    /**
     * 按 parent 链回溯成线性路径；断链则按 saved_at ASC。
     */
    static List<AdminCheckpointStepSummary> orderSteps(List<RawStep> rawSteps) {
        if (rawSteps == null || rawSteps.isEmpty()) {
            return List.of();
        }
        Map<String, RawStep> byId = new HashMap<>();
        Map<String, RawStep> byParent = new HashMap<>();
        for (RawStep step : rawSteps) {
            byId.put(step.checkpointId(), step);
            if (step.parentCheckpointId() != null) {
                byParent.putIfAbsent(step.parentCheckpointId(), step);
            }
        }

        List<RawStep> roots = rawSteps.stream()
                .filter(s -> s.parentCheckpointId() == null || !byId.containsKey(s.parentCheckpointId()))
                .sorted((a, b) -> {
                    OffsetDateTime sa = a.savedAt();
                    OffsetDateTime sb = b.savedAt();
                    if (sa == null && sb == null) {
                        return 0;
                    }
                    if (sa == null) {
                        return -1;
                    }
                    if (sb == null) {
                        return 1;
                    }
                    return sa.compareTo(sb);
                })
                .toList();

        List<RawStep> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (RawStep root : roots) {
            RawStep cur = root;
            while (cur != null && visited.add(cur.checkpointId())) {
                ordered.add(cur);
                cur = byParent.get(cur.checkpointId());
            }
        }
        // 断链/环遗漏：按时间补齐
        if (ordered.size() < rawSteps.size()) {
            List<RawStep> remaining = rawSteps.stream()
                    .filter(s -> !visited.contains(s.checkpointId()))
                    .sorted((a, b) -> {
                        OffsetDateTime sa = a.savedAt();
                        OffsetDateTime sb = b.savedAt();
                        if (sa == null && sb == null) {
                            return 0;
                        }
                        if (sa == null) {
                            return -1;
                        }
                        if (sb == null) {
                            return 1;
                        }
                        return sa.compareTo(sb);
                    })
                    .toList();
            ordered.addAll(remaining);
        }

        List<AdminCheckpointStepSummary> result = new ArrayList<>(ordered.size());
        OffsetDateTime prev = null;
        for (int i = 0; i < ordered.size(); i++) {
            RawStep step = ordered.get(i);
            Long delta = null;
            if (prev != null && step.savedAt() != null) {
                delta = step.savedAt().toInstant().toEpochMilli() - prev.toInstant().toEpochMilli();
            }
            result.add(new AdminCheckpointStepSummary(
                    step.checkpointId(),
                    step.parentCheckpointId(),
                    step.nodeId(),
                    step.nextNodeId(),
                    step.savedAt(),
                    step.stateContentType(),
                    delta,
                    i + 1));
            prev = step.savedAt();
        }
        return result;
    }

    /**
     * 解析 thread_name = userId:conversationId。
     */
    static ThreadNameParts parseThreadName(String threadName) {
        if (!StringUtils.hasText(threadName)) {
            return new ThreadNameParts(null, null);
        }
        int idx = threadName.indexOf(':');
        if (idx <= 0 || idx >= threadName.length() - 1) {
            return new ThreadNameParts(null, null);
        }
        return new ThreadNameParts(threadName.substring(0, idx), threadName.substring(idx + 1));
    }

    WhereClause buildThreadWhere(String threadName, Boolean isReleased, Instant savedFrom, Instant savedTo) {
        StringBuilder sql = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(threadName)) {
            appendClause(sql);
            sql.append("t.thread_name = ?");
            args.add(threadName.trim());
        }
        if (isReleased != null) {
            appendClause(sql);
            sql.append("t.is_released = ?");
            args.add(isReleased);
        }
        if (savedFrom != null || savedTo != null) {
            appendClause(sql);
            sql.append("EXISTS (SELECT 1 FROM graphcheckpoint cx WHERE cx.thread_id = t.thread_id");
            if (savedFrom != null) {
                sql.append(" AND cx.saved_at >= ?");
                args.add(Timestamp.from(savedFrom));
            }
            if (savedTo != null) {
                sql.append(" AND cx.saved_at <= ?");
                args.add(Timestamp.from(savedTo));
            }
            sql.append(')');
        }
        return new WhereClause(sql.toString(), args);
    }

    private static void appendClause(StringBuilder sql) {
        if (sql.isEmpty()) {
            sql.append(" WHERE ");
        } else {
            sql.append(" AND ");
        }
    }

    private JsonNode parseJson(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(raw.toString());
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(raw.toString());
        }
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    record RawStep(
            String checkpointId,
            String parentCheckpointId,
            String nodeId,
            String nextNodeId,
            OffsetDateTime savedAt,
            String stateContentType
    ) {
    }

    record ThreadNameParts(String userId, String conversationId) {
    }

    record WhereClause(String sql, List<Object> args) {
    }
}
