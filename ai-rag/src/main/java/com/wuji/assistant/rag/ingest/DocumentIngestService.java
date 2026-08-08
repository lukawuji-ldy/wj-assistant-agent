package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库入库：预处理 → 切分 → kb_chunk / revision → 写入 kb_chunk.embedding。
 *
 * @author liudy
 */
@Service
public class DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentPreprocessor preprocessor;
    private final ChineseRecursiveTextSplitter splitter;
    private final KbChunkEmbeddingService embeddingService;

    public DocumentIngestService(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 DocumentPreprocessor preprocessor,
                                 ChineseRecursiveTextSplitter splitter,
                                 KbChunkEmbeddingService embeddingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.preprocessor = preprocessor;
        this.splitter = splitter;
        this.embeddingService = embeddingService;
    }

    /**
     * 入库并激活版本。
     */
    public IngestResult ingest(IngestRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "content 不能为空");
        }
        String title = StringUtils.hasText(request.title()) ? request.title().trim() : "未命名文档";
        String collection = StringUtils.hasText(request.collection()) ? request.collection().trim() : "kb_default";
        String docId = StringUtils.hasText(request.docId()) ? request.docId().trim() : IdGenerator.nextBizId("doc_");
        SplitOptions resolved = splitter.resolve(request.splitOptions());
        String cleaned = preprocessor.preprocess(request.content());
        List<TextSplitter.TextChunk> chunks = splitter.split(cleaned, request.splitOptions());
        if (chunks.isEmpty()) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "切分后无有效内容");
        }

        List<String> aclRoles = normalizeAcl(request.aclRoles());
        String aclJson = toJson(aclRoles);
        String ingestOptionsJson = toJson(buildIngestOptions(resolved, request));

        Timestamp now = Timestamp.from(Instant.now());
        ensureDocument(docId, collection, title, now);
        String version = nextVersion(docId);
        long versionId = IdGenerator.nextLong();
        deprecateActiveVersions(docId, now);
        jdbcTemplate.update("""
                INSERT INTO kb_document_version
                (id, doc_id, version, status, source, acl_roles, ingest_options, published_at, create_time, update_time)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """,
                versionId, docId, version,
                request.source() == null ? "api" : request.source(),
                aclJson, ingestOptionsJson,
                now, now, now);
        jdbcTemplate.update("""
                UPDATE kb_document SET current_version_id = ?, title = ?, update_time = ?
                WHERE doc_id = ?
                """, versionId, title, now, docId);

        int i = 0;
        for (TextSplitter.TextChunk chunk : chunks) {
            i++;
            UUID chunkId = UUID.randomUUID();
            String chunkKey = docId + "_v" + version + "_c" + i;
            String content = chunk.content() == null ? "" : chunk.content();
            String hash = ContentHashes.sha256Hex(content);
            String section = chunk.section() == null ? "" : chunk.section();
            String summary = summaryOf(content);
            jdbcTemplate.update("""
                    INSERT INTO kb_chunk
                    (chunk_id, version_id, doc_id, collection, chunk_seq, chunk_key,
                     current_revision, section, summary, status, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, 'ACTIVE', ?, ?)
                    """,
                    chunkId, versionId, docId, collection, i, chunkKey,
                    section, summary, now, now);
            jdbcTemplate.update("""
                    INSERT INTO kb_chunk_revision
                    (chunk_id, revision, content, content_hash, status, create_time)
                    VALUES (?, 1, ?, ?, 'ACTIVE', ?)
                    """, chunkId, content, hash, now);
        }

        int embeddedCount = embeddingService.embedVersion(versionId);
        boolean embedded = embeddedCount > 0;

        log.info("ingested docId={} version={} chunks={} embedded={} embeddedCount={}",
                docId, version, chunks.size(), embedded, embeddedCount);
        return new IngestResult(docId, versionId, version, chunks.size(), embedded);
    }

    /**
     * 停用版本（不删历史，改 status）。
     */
    public void deprecate(String docId, long versionId) {
        Timestamp now = Timestamp.from(Instant.now());
        int n = jdbcTemplate.update("""
                UPDATE kb_document_version
                SET status = 'DEPRECATED', deprecated_at = ?, update_time = ?
                WHERE doc_id = ? AND id = ? AND status = 'ACTIVE'
                """, now, now, docId, versionId);
        if (n == 0) {
            throw new WujiException(ErrorCode.NOT_FOUND, "版本不存在或已停用");
        }
        jdbcTemplate.update("""
                UPDATE kb_chunk SET status = 'DEPRECATED', update_time = ?
                WHERE version_id = ? AND status = 'ACTIVE'
                """, now, versionId);
        jdbcTemplate.update("""
                UPDATE vector_store
                SET metadata = jsonb_set(metadata::jsonb, '{status}', '"DEPRECATED"'::jsonb, true)
                WHERE metadata ->> 'doc_id' = ? AND metadata ->> 'version_id' = ?
                """, docId, String.valueOf(versionId));
    }

    private Map<String, Object> buildIngestOptions(SplitOptions resolved, IngestRequest request) {
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("chunkSize", resolved.chunkSize());
        opts.put("overlap", resolved.overlap());
        opts.put("minChunkLengthToKeep", resolved.minChunkLengthToKeep());
        opts.put("chapterSplitEnabled", resolved.chapterSplitEnabled());
        if (StringUtils.hasText(request.sourceFile())) {
            opts.put("sourceFile", request.sourceFile().trim());
        } else if (StringUtils.hasText(request.source())) {
            opts.put("sourceFile", request.source().trim());
        }
        if (StringUtils.hasText(request.parser())) {
            opts.put("parser", request.parser().trim());
        } else {
            opts.put("parser", "plaintext");
        }
        return opts;
    }

    private static List<String> normalizeAcl(List<String> aclRoles) {
        if (aclRoles == null || aclRoles.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String role : aclRoles) {
            if (StringUtils.hasText(role)) {
                out.add(role.trim());
            }
        }
        return List.copyOf(out);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "json serialize failed", e);
        }
    }

    private void ensureDocument(String docId, String collection, String title, Timestamp now) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document WHERE doc_id = ?", Integer.class, docId);
        if (cnt != null && cnt > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO kb_document
                (id, doc_id, collection, title, current_version_id, create_time, update_time)
                VALUES (?, ?, ?, ?, NULL, ?, ?)
                """, IdGenerator.nextLong(), docId, collection, title, now, now);
    }

    private void deprecateActiveVersions(String docId, Timestamp now) {
        List<Long> activeIds = jdbcTemplate.queryForList("""
                SELECT id FROM kb_document_version WHERE doc_id = ? AND status = 'ACTIVE'
                """, Long.class, docId);
        jdbcTemplate.update("""
                UPDATE kb_document_version
                SET status = 'DEPRECATED', deprecated_at = ?, update_time = ?
                WHERE doc_id = ? AND status = 'ACTIVE'
                """, now, now, docId);
        for (Long vid : activeIds) {
            jdbcTemplate.update("""
                    UPDATE kb_chunk SET status = 'DEPRECATED', update_time = ?
                    WHERE version_id = ? AND status = 'ACTIVE'
                    """, now, vid);
        }
        jdbcTemplate.update("""
                UPDATE vector_store
                SET metadata = jsonb_set(metadata::jsonb, '{status}', '"DEPRECATED"'::jsonb, true)
                WHERE metadata ->> 'doc_id' = ? AND metadata ->> 'status' = 'ACTIVE'
                """, docId);
    }

    private String nextVersion(String docId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document_version WHERE doc_id = ?", Integer.class, docId);
        int n = cnt == null ? 0 : cnt;
        return "v" + (n + 1);
    }

    public static String summaryOf(String content) {
        if (content == null) {
            return "";
        }
        String s = content.trim().replace('\n', ' ');
        return s.length() <= 80 ? s : s.substring(0, 80);
    }

    /**
     * 入库时间 ISO-8601 UTC，毫秒精度（如 2026-08-07T10:11:12.345Z）。
     */
    static String formatIngestedAt(Instant instant) {
        if (instant == null) {
            return null;
        }
        Instant truncated = instant.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return truncated.toString();
    }

    static String toVectorLiteral(float[] vector) {
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
