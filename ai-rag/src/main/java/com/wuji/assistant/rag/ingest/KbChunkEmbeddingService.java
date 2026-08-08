package com.wuji.assistant.rag.ingest;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
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
 * 将向量写入 kb_chunk，模型指纹写入 kb_document_version（无独立 set 表）。
 *
 * @author liudy
 */
@Service
public class KbChunkEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(KbChunkEmbeddingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;

    public KbChunkEmbeddingService(JdbcTemplate jdbcTemplate,
                                   ObjectProvider<EmbeddingClient> embeddingClients) {
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
    }

    /**
     * 对该 version 下全部 ACTIVE chunk 原地嵌入，并更新版本指纹。
     *
     * @return 成功写入向量的 chunk 数
     */
    public int embedVersion(long versionId) {
        ensureVersionExists(versionId);
        List<Map<String, Object>> chunks = jdbcTemplate.queryForList("""
                SELECT c.chunk_id::text AS chunk_id, c.current_revision, r.content, r.content_hash
                FROM kb_chunk c
                JOIN kb_chunk_revision r
                  ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision AND r.status = 'ACTIVE'
                WHERE c.version_id = ? AND c.status = 'ACTIVE'
                ORDER BY c.chunk_seq ASC
                """, versionId);

        int embedded = 0;
        for (Map<String, Object> row : chunks) {
            UUID chunkId = UUID.fromString(String.valueOf(row.get("chunk_id")));
            int revision = ((Number) row.get("current_revision")).intValue();
            String content = row.get("content") == null ? "" : String.valueOf(row.get("content"));
            String hash = String.valueOf(row.get("content_hash"));
            if (writeChunkEmbedding(chunkId, content, revision, hash)) {
                embedded++;
            }
        }

        if (embedded > 0) {
            updateVersionFingerprint(versionId);
        }
        log.info("embedVersion versionId={} chunks={} embedded={}", versionId, chunks.size(), embedded);
        return embedded;
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
     */
    public boolean refreshChunk(UUID chunkId, String content, int revision, String contentHash) {
        boolean ok = writeChunkEmbedding(chunkId, content, revision, contentHash);
        if (ok) {
            Long versionId = jdbcTemplate.queryForObject(
                    "SELECT version_id FROM kb_chunk WHERE chunk_id = ?", Long.class, chunkId);
            if (versionId != null) {
                String fp = jdbcTemplate.queryForObject("""
                        SELECT embedding_model_version FROM kb_document_version WHERE id = ?
                        """, String.class, versionId);
                if (!StringUtils.hasText(fp)) {
                    updateVersionFingerprint(versionId);
                }
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
    }

    public KbVersionEmbeddingView getVersionEmbedding(long versionId) {
        ensureVersionExists(versionId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT embedding_config_id, embedding_model_version
                FROM kb_document_version WHERE id = ?
                """, versionId);
        Map<String, Object> row = rows.get(0);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM kb_chunk
                WHERE version_id = ? AND status = 'ACTIVE' AND embedding IS NOT NULL
                """, Integer.class, versionId);
        String configId = row.get("embedding_config_id") == null
                ? null : String.valueOf(row.get("embedding_config_id"));
        String modelVersion = row.get("embedding_model_version") == null
                ? null : String.valueOf(row.get("embedding_model_version"));
        return new KbVersionEmbeddingView(
                versionId,
                configId,
                modelVersion,
                count == null ? 0 : count
        );
    }

    private boolean writeChunkEmbedding(UUID chunkId, String content, int revision, String contentHash) {
        if (!embeddingClient.available()) {
            return false;
        }
        float[] vector = embeddingClient.embed(content);
        if (vector == null) {
            return false;
        }
        Timestamp now = Timestamp.from(Instant.now());
        String literal = DocumentIngestService.toVectorLiteral(vector);
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding = ?::vector,
                    embedding_content_revision = ?,
                    embedding_content_hash = ?,
                    update_time = ?
                WHERE chunk_id = ?
                """, literal, revision, contentHash, now, chunkId);
        return true;
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
