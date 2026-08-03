package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库入库：预处理 → 切分 → Embedding → vector_store。
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
    private final EmbeddingClient embeddingClient;

    public DocumentIngestService(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 DocumentPreprocessor preprocessor,
                                 ChineseRecursiveTextSplitter splitter,
                                 ObjectProvider<EmbeddingClient> embeddingClients) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.preprocessor = preprocessor;
        this.splitter = splitter;
        this.embeddingClient = embeddingClients.getIfAvailable(() -> new EmbeddingClient() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public float[] embed(String text) {
                return null;
            }
        });
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
        String cleaned = preprocessor.preprocess(request.content());
        List<TextSplitter.TextChunk> chunks = splitter.split(cleaned);
        if (chunks.isEmpty()) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "切分后无有效内容");
        }

        Timestamp now = Timestamp.from(Instant.now());
        ensureDocument(docId, collection, title, now);
        String version = nextVersion(docId);
        long versionId = IdGenerator.nextLong();
        deprecateActiveVersions(docId, now);
        jdbcTemplate.update("""
                INSERT INTO kb_document_version
                (id, doc_id, version, status, source, acl_roles, published_at, create_time, update_time)
                VALUES (?, ?, ?, 'ACTIVE', ?, '[]'::jsonb, ?, ?, ?)
                """,
                versionId, docId, version,
                request.source() == null ? "api" : request.source(),
                now, now, now);
        jdbcTemplate.update("""
                UPDATE kb_document SET current_version_id = ?, title = ?, update_time = ?
                WHERE doc_id = ?
                """, versionId, title, now, docId);

        // 按 doc_id+version_id 替换：先删同 version 旧向量（若重入）
        jdbcTemplate.update("""
                DELETE FROM vector_store
                WHERE metadata ->> 'doc_id' = ? AND metadata ->> 'version_id' = ?
                """, docId, String.valueOf(versionId));

        boolean embedded = embeddingClient.available();
        int i = 0;
        for (TextSplitter.TextChunk chunk : chunks) {
            i++;
            String chunkId = docId + "_v" + version + "_c" + i;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("doc_id", docId);
            meta.put("version", version);
            meta.put("version_id", versionId);
            meta.put("section", chunk.section() == null ? "" : chunk.section());
            meta.put("chunk_id", chunkId);
            meta.put("summary", summaryOf(chunk.content()));
            meta.put("collection", collection);
            meta.put("status", "ACTIVE");
            String metaJson;
            try {
                metaJson = objectMapper.writeValueAsString(meta);
            } catch (Exception e) {
                throw new WujiException(ErrorCode.INTERNAL_ERROR, "metadata serialize failed", e);
            }
            float[] vector = embedded ? embeddingClient.embed(chunk.content()) : null;
            if (vector != null) {
                jdbcTemplate.update("""
                        INSERT INTO vector_store (id, content, metadata, embedding)
                        VALUES (?::uuid, ?, ?::jsonb, ?::vector)
                        """,
                        UUID.randomUUID().toString(), chunk.content(), metaJson, toVectorLiteral(vector));
            } else {
                embedded = false;
                jdbcTemplate.update("""
                        INSERT INTO vector_store (id, content, metadata, embedding)
                        VALUES (?::uuid, ?, ?::jsonb, NULL)
                        """,
                        UUID.randomUUID().toString(), chunk.content(), metaJson);
            }
        }
        log.info("ingested docId={} version={} chunks={} embedded={}", docId, version, chunks.size(), embedded);
        return new IngestResult(docId, versionId, version, chunks.size(), embedded);
    }

    /**
     * 停用版本（不删向量，改 status）。
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
                UPDATE vector_store
                SET metadata = jsonb_set(metadata::jsonb, '{status}', '"DEPRECATED"'::jsonb, true)
                WHERE metadata ->> 'doc_id' = ? AND metadata ->> 'version_id' = ?
                """, docId, String.valueOf(versionId));
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
        jdbcTemplate.update("""
                UPDATE kb_document_version
                SET status = 'DEPRECATED', deprecated_at = ?, update_time = ?
                WHERE doc_id = ? AND status = 'ACTIVE'
                """, now, now, docId);
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

    private static String summaryOf(String content) {
        if (content == null) {
            return "";
        }
        String s = content.trim().replace('\n', ' ');
        return s.length() <= 80 ? s : s.substring(0, 80);
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
