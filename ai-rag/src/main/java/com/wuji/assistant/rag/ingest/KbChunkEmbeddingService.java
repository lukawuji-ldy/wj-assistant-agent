package com.wuji.assistant.rag.ingest;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.config.RagVectorProperties;
import com.wuji.assistant.rag.vector.VectorChunkDocument;
import com.wuji.assistant.rag.vector.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 将向量写入 VectorIndexPort（PG 列或 ES 投影），模型指纹写入 kb_document_version。
 *
 * @author liudy
 */
@Service
public class KbChunkEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(KbChunkEmbeddingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;
    private final VectorIndexPort vectorIndexPort;
    private final RagVectorProperties ragVectorProperties;
    private final EmbeddingJobProgressTracker progressTracker;

    public KbChunkEmbeddingService(JdbcTemplate jdbcTemplate,
                                   ObjectProvider<EmbeddingClient> embeddingClients,
                                   VectorIndexPort vectorIndexPort,
                                   RagVectorProperties ragVectorProperties,
                                   EmbeddingJobProgressTracker progressTracker) {
        this.jdbcTemplate = jdbcTemplate;
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
        this.vectorIndexPort = vectorIndexPort;
        this.ragVectorProperties = ragVectorProperties;
        this.progressTracker = progressTracker;
    }

    /**
     * 对该 version 下全部 ACTIVE chunk 原地嵌入，并更新版本指纹。
     * 带固定请求间隔与 429 退避（见 {@code wuji.rag.embedding.*}）。
     *
     * @return 成功写入向量的 chunk 数
     */
    public int embedVersion(long versionId) {
        ensureVersionExists(versionId);
        List<Map<String, Object>> chunks = jdbcTemplate.queryForList("""
                SELECT c.chunk_id::text AS chunk_id, c.doc_id, c.version_id, c.collection,
                       c.section, c.summary, c.status, v.status AS version_status,
                       c.current_revision, r.content, r.content_hash
                FROM kb_chunk c
                JOIN kb_document_version v ON v.id = c.version_id
                JOIN kb_chunk_revision r
                  ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision AND r.status = 'ACTIVE'
                WHERE c.version_id = ? AND c.status = 'ACTIVE'
                ORDER BY c.chunk_seq ASC
                """, versionId);

        EmbeddingBulkThrottle throttle = new EmbeddingBulkThrottle(ragVectorProperties.getEmbedding());
        progressTracker.start(versionId, chunks.size());
        int embedded = 0;
        try {
            for (Map<String, Object> row : chunks) {
                UUID chunkId = UUID.fromString(String.valueOf(row.get("chunk_id")));
                int revision = ((Number) row.get("current_revision")).intValue();
                String content = row.get("content") == null ? "" : String.valueOf(row.get("content"));
                String hash = String.valueOf(row.get("content_hash"));
                boolean ok = writeChunkEmbeddingBulk(versionId, chunkId, row, content, revision, hash, throttle);
                progressTracker.tick(versionId, String.valueOf(chunkId), ok);
                if (ok) {
                    embedded++;
                }
            }
            if (embedded > 0) {
                updateVersionFingerprint(versionId);
            }
            progressTracker.succeed(versionId);
        } catch (RuntimeException ex) {
            progressTracker.fail(versionId, safeProgressMessage(ex));
            throw ex;
        }
        log.info("embedVersion versionId={} chunks={} embedded={}", versionId, chunks.size(), embedded);
        return embedded;
    }

    public EmbeddingJobProgress getProgress(long versionId) {
        ensureVersionExists(versionId);
        return progressTracker.snapshot(versionId);
    }

    /**
     * 按当前 Embedding 配置原地全量重嵌。
     */
    public KbVersionEmbeddingView rebuildForVersion(long versionId) {
        embedVersion(versionId);
        return getVersionEmbedding(versionId);
    }

    /**
     * 刷新单个 chunk 的向量；若版本尚无指纹且写入成功则补写指纹。
     * 不限速（非批量路径）。
     */
    public boolean refreshChunk(UUID chunkId, String content, int revision, String contentHash) {
        Map<String, Object> row = loadChunkRow(chunkId);
        if (row == null) {
            return false;
        }
        boolean ok = writeChunkEmbedding(chunkId, row, content, revision, contentHash);
        if (ok) {
            Long versionId = ((Number) row.get("version_id")).longValue();
            String fp = jdbcTemplate.queryForObject("""
                    SELECT embedding_model_version FROM kb_document_version WHERE id = ?
                    """, String.class, versionId);
            if (!StringUtils.hasText(fp)) {
                updateVersionFingerprint(versionId);
            }
        }
        return ok;
    }

    public void clearChunkEmbedding(UUID chunkId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding = NULL, embedding_content_revision = NULL,
                    embedding_content_hash = NULL, update_time = ?
                WHERE chunk_id = ?
                """, now, chunkId);
        vectorIndexPort.deprecateChunk(chunkId);
    }

    public KbVersionEmbeddingView getVersionEmbedding(long versionId) {
        ensureVersionExists(versionId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT embedding_config_id, embedding_model_version
                FROM kb_document_version WHERE id = ?
                """, versionId);
        Map<String, Object> row = rows.get(0);
        String configId = row.get("embedding_config_id") == null
                ? null : String.valueOf(row.get("embedding_config_id"));
        String modelVersion = row.get("embedding_model_version") == null
                ? null : String.valueOf(row.get("embedding_model_version"));
        return new KbVersionEmbeddingView(
                versionId,
                configId,
                modelVersion,
                vectorIndexPort.embeddedCount(versionId),
                vectorIndexPort.vectorBackend(),
                vectorIndexPort.indexName()
        );
    }

    private boolean writeChunkEmbeddingBulk(long versionId, UUID chunkId, Map<String, Object> row,
                                            String content, int revision, String contentHash,
                                            EmbeddingBulkThrottle throttle) {
        int attempt = 0;
        while (true) {
            throttle.awaitInterval();
            try {
                boolean ok = writeChunkEmbedding(chunkId, row, content, revision, contentHash);
                if (ok) {
                    throttle.markSuccess();
                }
                return ok;
            } catch (RuntimeException ex) {
                if (!EmbeddingBulkThrottle.isRateLimitError(ex)
                        || !throttle.backoffAndRetry(attempt, String.valueOf(chunkId))) {
                    throw ex;
                }
                progressTracker.waiting(versionId, "厂商限流，退避重试中");
                attempt++;
            }
        }
    }

    private static String safeProgressMessage(Throwable ex) {
        String msg = ex.getMessage();
        if (!StringUtils.hasText(msg)) {
            return ex.getClass().getSimpleName();
        }
        String oneLine = msg.replace('\n', ' ').trim();
        return oneLine.length() > 180 ? oneLine.substring(0, 180) : oneLine;
    }

    private boolean writeChunkEmbedding(UUID chunkId, Map<String, Object> row,
                                        String content, int revision, String contentHash) {
        if (!embeddingClient.available()) {
            return false;
        }
        float[] vector = embeddingClient.embed(content);
        if (vector == null) {
            return false;
        }
        int expectedDims = embeddingClient.dimensions();
        if (vector.length != expectedDims) {
            throw new WujiException(ErrorCode.RAG_UNAVAILABLE, """
                    Embedding 向量维度 %d 与 llm_config.extra_json.dimensions=%d 不一致。
                    请核对 EMBEDDING 配置的 model 与 dimensions 字段。
                    """.formatted(vector.length, expectedDims).trim());
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding_content_revision = ?,
                    embedding_content_hash = ?,
                    update_time = ?
                WHERE chunk_id = ?
                """, revision, contentHash, now, chunkId);
        VectorChunkDocument document = toDocument(chunkId, row, content, revision, contentHash, vector);
        vectorIndexPort.upsertChunk(document);
        return true;
    }

    private VectorChunkDocument toDocument(UUID chunkId, Map<String, Object> row,
                                           String content, int revision, String contentHash,
                                           float[] vector) {
        return new VectorChunkDocument(
                chunkId,
                String.valueOf(row.get("doc_id")),
                ((Number) row.get("version_id")).longValue(),
                row.get("collection") == null ? "" : String.valueOf(row.get("collection")),
                row.get("status") == null ? "ACTIVE" : String.valueOf(row.get("status")),
                row.get("version_status") == null ? "ACTIVE" : String.valueOf(row.get("version_status")),
                revision,
                contentHash,
                row.get("section") == null ? "" : String.valueOf(row.get("section")),
                row.get("summary") == null ? "" : String.valueOf(row.get("summary")),
                content,
                vector
        );
    }

    private Map<String, Object> loadChunkRow(UUID chunkId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT c.chunk_id, c.doc_id, c.version_id, c.collection, c.section, c.summary,
                       c.status, v.status AS version_status
                FROM kb_chunk c
                JOIN kb_document_version v ON v.id = c.version_id
                WHERE c.chunk_id = ?
                """, chunkId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void updateVersionFingerprint(long versionId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE kb_document_version
                SET embedding_config_id = ?, embedding_model_version = ?, update_time = ?
                WHERE id = ?
                """,
                embeddingClient.embeddingConfigId(),
                embeddingClient.embeddingModelVersion(),
                now,
                versionId);
    }

    private void ensureVersionExists(long versionId) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document_version WHERE id = ?", Integer.class, versionId);
        if (cnt == null || cnt == 0) {
            throw new WujiException(ErrorCode.NOT_FOUND, "版本不存在");
        }
    }
}
