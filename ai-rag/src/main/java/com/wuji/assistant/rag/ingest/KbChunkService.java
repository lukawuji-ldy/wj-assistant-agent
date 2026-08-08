package com.wuji.assistant.rag.ingest;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库 chunk CRUD（kb_chunk + revision；向量写 kb_chunk.embedding）。
 *
 * @author liudy
 */
@Service
public class KbChunkService {

    private final JdbcTemplate jdbcTemplate;
    private final KbChunkEmbeddingService embeddingService;

    public KbChunkService(JdbcTemplate jdbcTemplate, KbChunkEmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
    }

    public List<KbChunkView> listByVersion(long versionId, String status) {
        ensureVersionExists(versionId);
        String sql;
        Object[] args;
        if (StringUtils.hasText(status)) {
            sql = """
                    SELECT c.chunk_id::text AS id, c.chunk_key, c.chunk_seq, c.section, c.summary,
                           c.status, c.collection, c.doc_id, c.version_id, c.current_revision,
                           r.content, r.content_hash, r.create_time AS revision_time,
                           v.version
                    FROM kb_chunk c
                    JOIN kb_document_version v ON v.id = c.version_id
                    LEFT JOIN kb_chunk_revision r
                      ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision
                    WHERE c.version_id = ? AND c.status = ?
                    ORDER BY c.chunk_seq ASC, c.chunk_id ASC
                    """;
            args = new Object[]{versionId, status.trim()};
        } else {
            sql = """
                    SELECT c.chunk_id::text AS id, c.chunk_key, c.chunk_seq, c.section, c.summary,
                           c.status, c.collection, c.doc_id, c.version_id, c.current_revision,
                           r.content, r.content_hash, r.create_time AS revision_time,
                           v.version
                    FROM kb_chunk c
                    JOIN kb_document_version v ON v.id = c.version_id
                    LEFT JOIN kb_chunk_revision r
                      ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision
                    WHERE c.version_id = ?
                    ORDER BY c.chunk_seq ASC, c.chunk_id ASC
                    """;
            args = new Object[]{versionId};
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        List<KbChunkView> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(toView(row));
        }
        return out;
    }

    public KbChunkWriteResult create(long versionId, String content, String section) {
        if (!StringUtils.hasText(content)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "content 不能为空");
        }
        Map<String, Object> version = loadVersion(versionId);
        String docId = String.valueOf(version.get("doc_id"));
        String versionLabel = String.valueOf(version.get("version"));
        String versionStatus = String.valueOf(version.get("status"));
        String collection = loadCollection(docId);

        int nextIndex = nextChunkSeq(versionId);
        Instant nowInstant = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Timestamp now = Timestamp.from(nowInstant);
        UUID chunkId = UUID.randomUUID();
        String chunkKey = docId + "_v" + versionLabel + "_c" + nextIndex;
        String chunkStatus = "ACTIVE".equalsIgnoreCase(versionStatus) ? "ACTIVE" : "DEPRECATED";
        String sec = section == null ? "" : section.trim();
        String body = content.trim();
        String hash = ContentHashes.sha256Hex(body);
        String summary = DocumentIngestService.summaryOf(body);

        jdbcTemplate.update("""
                INSERT INTO kb_chunk
                (chunk_id, version_id, doc_id, collection, chunk_seq, chunk_key,
                 current_revision, section, summary, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?)
                """, chunkId, versionId, docId, collection, nextIndex, chunkKey,
                sec, summary, chunkStatus, now, now);
        jdbcTemplate.update("""
                INSERT INTO kb_chunk_revision
                (chunk_id, revision, content, content_hash, status, create_time)
                VALUES (?, 1, ?, ?, 'ACTIVE', ?)
                """, chunkId, body, hash, now);

        boolean embedded = false;
        if ("ACTIVE".equals(chunkStatus)) {
            embedded = embeddingService.refreshChunk(chunkId, body, 1, hash);
        }
        return new KbChunkWriteResult(getById(chunkId.toString()), embedded);
    }

    /**
     * 更新正文：hash 相同 no-op；否则新 revision + 刷新该 chunk 向量。
     */
    public KbChunkWriteResult update(String chunkId, String content, String section) {
        if (!StringUtils.hasText(chunkId)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "chunkId 不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "content 不能为空");
        }
        UUID id = parseUuid(chunkId);
        Map<String, Object> chunk = loadChunk(id);
        if ("DEPRECATED".equalsIgnoreCase(String.valueOf(chunk.get("status")))) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "已停用 chunk 不可编辑");
        }
        long versionId = ((Number) chunk.get("version_id")).longValue();
        int currentRev = ((Number) chunk.get("current_revision")).intValue();
        String body = content.trim();
        String newHash = ContentHashes.sha256Hex(body);

        String activeHash = jdbcTemplate.queryForObject("""
                SELECT content_hash FROM kb_chunk_revision
                WHERE chunk_id = ? AND revision = ? AND status = 'ACTIVE'
                """, String.class, id, currentRev);
        boolean sectionChanged = section != null
                && !section.trim().equals(chunk.get("section") == null ? "" : String.valueOf(chunk.get("section")));
        if (newHash.equals(activeHash) && !sectionChanged) {
            return new KbChunkWriteResult(getById(chunkId), false);
        }

        Timestamp now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        if (newHash.equals(activeHash) && sectionChanged) {
            jdbcTemplate.update("""
                    UPDATE kb_chunk SET section = ?, update_time = ? WHERE chunk_id = ?
                    """, section.trim(), now, id);
            return new KbChunkWriteResult(getById(chunkId), false);
        }

        int nextRev = currentRev + 1;
        jdbcTemplate.update("""
                UPDATE kb_chunk_revision SET status = 'DEPRECATED'
                WHERE chunk_id = ? AND status = 'ACTIVE'
                """, id);
        jdbcTemplate.update("""
                INSERT INTO kb_chunk_revision
                (chunk_id, revision, content, content_hash, status, create_time)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?)
                """, id, nextRev, body, newHash, now);
        String sec = section != null ? section.trim()
                : (chunk.get("section") == null ? "" : String.valueOf(chunk.get("section")));
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET current_revision = ?, section = ?, summary = ?, update_time = ?
                WHERE chunk_id = ?
                """, nextRev, sec, DocumentIngestService.summaryOf(body), now, id);

        boolean embedded = embeddingService.refreshChunk(id, body, nextRev, newHash);
        return new KbChunkWriteResult(getById(chunkId), embedded);
    }

    /**
     * 逻辑停用；从 ACTIVE set 移除向量；保留 revision 历史。
     */
    public void delete(String chunkId) {
        UUID id = parseUuid(chunkId);
        loadChunk(id);
        Timestamp now = Timestamp.from(Instant.now());
        int n = jdbcTemplate.update("""
                UPDATE kb_chunk SET status = 'DEPRECATED', update_time = ?
                WHERE chunk_id = ? AND status = 'ACTIVE'
                """, now, id);
        if (n == 0) {
            throw new WujiException(ErrorCode.NOT_FOUND, "chunk 不存在或已停用");
        }
        embeddingService.clearChunkEmbedding(id);
    }

    public KbChunkView getById(String chunkId) {
        UUID id = parseUuid(chunkId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT c.chunk_id::text AS id, c.chunk_key, c.chunk_seq, c.section, c.summary,
                       c.status, c.collection, c.doc_id, c.version_id, c.current_revision,
                       r.content, r.content_hash, r.create_time AS revision_time,
                       v.version
                FROM kb_chunk c
                JOIN kb_document_version v ON v.id = c.version_id
                LEFT JOIN kb_chunk_revision r
                  ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision
                WHERE c.chunk_id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new WujiException(ErrorCode.NOT_FOUND, "chunk 不存在");
        }
        return toView(rows.get(0));
    }

    public List<KbChunkRevisionView> listRevisions(String chunkId) {
        UUID id = parseUuid(chunkId);
        loadChunk(id);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT chunk_id::text AS chunk_id, revision, content_hash, status, content, create_time
                FROM kb_chunk_revision
                WHERE chunk_id = ?
                ORDER BY revision DESC
                """, id);
        List<KbChunkRevisionView> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            out.add(new KbChunkRevisionView(
                    String.valueOf(row.get("chunk_id")),
                    ((Number) row.get("revision")).intValue(),
                    String.valueOf(row.get("content_hash")),
                    String.valueOf(row.get("status")),
                    row.get("content") == null ? "" : String.valueOf(row.get("content")),
                    formatTime(row.get("create_time"))
            ));
        }
        return out;
    }

    public KbChunkWriteResult rollback(String chunkId, int revision) {
        UUID id = parseUuid(chunkId);
        Map<String, Object> chunk = loadChunk(id);
        if ("DEPRECATED".equalsIgnoreCase(String.valueOf(chunk.get("status")))) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "已停用 chunk 不可回滚");
        }
        long versionId = ((Number) chunk.get("version_id")).longValue();
        List<Map<String, Object>> targetRows = jdbcTemplate.queryForList("""
                SELECT content, content_hash FROM kb_chunk_revision
                WHERE chunk_id = ? AND revision = ?
                """, id, revision);
        if (targetRows.isEmpty()) {
            throw new WujiException(ErrorCode.NOT_FOUND, "revision 不存在");
        }
        int currentRev = ((Number) chunk.get("current_revision")).intValue();
        if (currentRev == revision) {
            return new KbChunkWriteResult(getById(chunkId), false);
        }

        Timestamp now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        jdbcTemplate.update("""
                UPDATE kb_chunk_revision SET status = 'DEPRECATED'
                WHERE chunk_id = ? AND status = 'ACTIVE'
                """, id);
        jdbcTemplate.update("""
                UPDATE kb_chunk_revision SET status = 'ACTIVE'
                WHERE chunk_id = ? AND revision = ?
                """, id, revision);
        String content = String.valueOf(targetRows.get(0).get("content"));
        String hash = String.valueOf(targetRows.get(0).get("content_hash"));
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET current_revision = ?, summary = ?, update_time = ?
                WHERE chunk_id = ?
                """, revision, DocumentIngestService.summaryOf(content), now, id);

        boolean embedded = embeddingService.refreshChunk(id, content, revision, hash);
        return new KbChunkWriteResult(getById(chunkId), embedded);
    }

    private int nextChunkSeq(long versionId) {
        Integer max = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(chunk_seq), 0) FROM kb_chunk WHERE version_id = ?
                """, Integer.class, versionId);
        return (max == null ? 0 : max) + 1;
    }

    private void ensureVersionExists(long versionId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document_version WHERE id = ?", Integer.class, versionId);
        if (cnt == null || cnt == 0) {
            throw new WujiException(ErrorCode.NOT_FOUND, "版本不存在");
        }
    }

    private Map<String, Object> loadVersion(long versionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, doc_id, version, status, acl_roles
                FROM kb_document_version WHERE id = ?
                """, versionId);
        if (rows.isEmpty()) {
            throw new WujiException(ErrorCode.NOT_FOUND, "版本不存在");
        }
        return rows.get(0);
    }

    private String loadCollection(String docId) {
        List<String> cols = jdbcTemplate.queryForList(
                "SELECT collection FROM kb_document WHERE doc_id = ?", String.class, docId);
        return cols.isEmpty() || cols.get(0) == null ? "kb_default" : cols.get(0);
    }

    private Map<String, Object> loadChunk(UUID chunkId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT chunk_id, version_id, current_revision, status, section
                FROM kb_chunk WHERE chunk_id = ?
                """, chunkId);
        if (rows.isEmpty()) {
            throw new WujiException(ErrorCode.NOT_FOUND, "chunk 不存在");
        }
        return rows.get(0);
    }

    private static UUID parseUuid(String chunkId) {
        try {
            return UUID.fromString(chunkId.trim());
        } catch (Exception e) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "chunkId 须为 UUID");
        }
    }

    private KbChunkView toView(Map<String, Object> row) {
        Long versionId = null;
        Object vid = row.get("version_id");
        if (vid instanceof Number number) {
            versionId = number.longValue();
        }
        Integer chunkSeq = null;
        Object seq = row.get("chunk_seq");
        if (seq instanceof Number number) {
            chunkSeq = number.intValue();
        }
        Integer currentRevision = null;
        Object rev = row.get("current_revision");
        if (rev instanceof Number number) {
            currentRevision = number.intValue();
        }
        return new KbChunkView(
                String.valueOf(row.get("id")),
                row.get("chunk_key") == null ? null : String.valueOf(row.get("chunk_key")),
                chunkSeq,
                formatTime(row.get("revision_time")),
                row.get("content") == null ? "" : String.valueOf(row.get("content")),
                row.get("section") == null ? "" : String.valueOf(row.get("section")),
                row.get("summary") == null ? "" : String.valueOf(row.get("summary")),
                row.get("status") == null ? "ACTIVE" : String.valueOf(row.get("status")),
                row.get("collection") == null ? "" : String.valueOf(row.get("collection")),
                row.get("doc_id") == null ? null : String.valueOf(row.get("doc_id")),
                versionId,
                row.get("version") == null ? null : String.valueOf(row.get("version")),
                currentRevision,
                row.get("content_hash") == null ? null : String.valueOf(row.get("content_hash"))
        );
    }

    private static String formatTime(Object column) {
        if (column instanceof Timestamp ts) {
            return DocumentIngestService.formatIngestedAt(ts.toInstant());
        }
        if (column instanceof OffsetDateTime odt) {
            return DocumentIngestService.formatIngestedAt(odt.toInstant());
        }
        if (column instanceof Instant instant) {
            return DocumentIngestService.formatIngestedAt(instant);
        }
        return null;
    }
}
