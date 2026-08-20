package com.wuji.assistant.rag.vector;

import com.wuji.assistant.rag.RetrievalQueryTerms;
import com.wuji.assistant.rag.RetrievalResult;
import com.wuji.assistant.rag.ingest.DocumentIngestService;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PGVector 适配器：余弦 + ILIKE 检索；向量写入 kb_chunk.embedding。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(name = "wuji.rag.vector-backend", havingValue = "pgvector", matchIfMissing = true)
public class PgVectorIndexAdapter implements VectorIndexPort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorIndexAdapter.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;

    public PgVectorIndexAdapter(JdbcTemplate jdbcTemplate,
                                ObjectProvider<EmbeddingClient> embeddingClients) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClients.getIfAvailable(() -> unavailableClient());
    }

    @Override
    public void upsertChunk(VectorChunkDocument document) {
        if (!embeddingClient.available() || document.embedding() == null) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        String literal = DocumentIngestService.toVectorLiteral(document.embedding());
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding = ?::vector,
                    embedding_content_revision = ?,
                    embedding_content_hash = ?,
                    update_time = ?
                WHERE chunk_id = ?
                """,
                literal,
                document.revision(),
                document.contentHash(),
                now,
                document.chunkId());
    }

    @Override
    public void deprecateChunk(UUID chunkId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding = NULL, embedding_content_revision = NULL,
                    embedding_content_hash = NULL, update_time = ?
                WHERE chunk_id = ?
                """, now, chunkId);
    }

    @Override
    public void deprecateVersion(long versionId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding = NULL, embedding_content_revision = NULL,
                    embedding_content_hash = NULL, update_time = ?
                WHERE version_id = ? AND status = 'DEPRECATED'
                """, now, versionId);
    }

    @Override
    public RetrievalResult search(String query, int topK, double minReliableScore) {
        if (!StringUtils.hasText(query)) {
            return new RetrievalResult(List.of(), true, "查询为空");
        }
        int limit = Math.max(1, topK);
        String q = query.trim();
        List<RetrievalResult.Hit> cosineHits = List.of();
        if (embeddingClient.available()) {
            cosineHits = cosineSearch(q, limit);
        } else {
            log.debug("embedding unavailable, keyword-only retrieval");
        }
        List<RetrievalResult.Hit> keywordHits = keywordSearch(q, limit);
        List<RetrievalResult.Hit> reliable = mergeHits(cosineHits, keywordHits, limit).stream()
                .filter(h -> h.score() >= minReliableScore)
                .toList();
        if (reliable.isEmpty()) {
            return new RetrievalResult(List.of(), true, "无可靠知识命中");
        }
        return new RetrievalResult(reliable, false, null);
    }

    @Override
    public int embeddedCount(long versionId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM kb_chunk
                WHERE version_id = ? AND status = 'ACTIVE' AND embedding IS NOT NULL
                """, Integer.class, versionId);
        return count == null ? 0 : count;
    }

    @Override
    public String vectorBackend() {
        return "pgvector";
    }

    @Override
    public String indexName() {
        return null;
    }

    public static List<RetrievalResult.Hit> mergeHits(List<RetrievalResult.Hit> cosineHits,
                                               List<RetrievalResult.Hit> keywordHits,
                                               int limit) {
        Map<String, RetrievalResult.Hit> byId = new LinkedHashMap<>();
        for (RetrievalResult.Hit hit : cosineHits) {
            byId.put(hit.chunkId(), hit);
        }
        for (RetrievalResult.Hit hit : keywordHits) {
            RetrievalResult.Hit old = byId.get(hit.chunkId());
            if (old == null || hit.score() > old.score()) {
                byId.put(hit.chunkId(), hit);
            }
        }
        return byId.values().stream()
                .sorted(Comparator.comparingDouble(RetrievalResult.Hit::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    public static List<String> likePatterns(String query) {
        LinkedHashSet<String> patterns = new LinkedHashSet<>();
        String full = RetrievalQueryTerms.likeLiteral(query);
        if (full.length() >= 2) {
            patterns.add("%" + full + "%");
        }
        for (String term : RetrievalQueryTerms.terms(query)) {
            String lit = RetrievalQueryTerms.likeLiteral(term);
            if (lit.length() >= 2) {
                patterns.add("%" + lit + "%");
            }
        }
        return new ArrayList<>(patterns);
    }

    private List<RetrievalResult.Hit> cosineSearch(String query, int limit) {
        float[] vector = embeddingClient.embed(query);
        if (vector == null) {
            return keywordSearch(query, limit);
        }
        String literal = DocumentIngestService.toVectorLiteral(vector);
        List<RetrievalResult.Hit> hits = new ArrayList<>();
        try {
            jdbcTemplate.query("""
                            SELECT c.chunk_id::text AS chunk_id,
                                   r.content AS content,
                                   c.current_revision AS revision,
                                   r.content_hash AS content_hash,
                                   c.doc_id, c.collection, c.section, c.summary, c.chunk_key,
                                   c.version_id, v.version, v.embedding_config_id,
                                   v.embedding_model_version,
                                   (1 - (c.embedding <=> ?::vector)) AS score
                            FROM kb_chunk c
                            JOIN kb_document_version v ON v.id = c.version_id AND v.status = 'ACTIVE'
                            JOIN kb_chunk_revision r
                              ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision AND r.status = 'ACTIVE'
                            WHERE c.status = 'ACTIVE'
                              AND c.embedding IS NOT NULL
                            ORDER BY c.embedding <=> ?::vector
                            LIMIT ?
                            """,
                    rs -> {
                        while (rs.next()) {
                            hits.add(toHit(
                                    rs.getString("chunk_id"),
                                    rs.getString("content"),
                                    rs.getDouble("score"),
                                    rs));
                        }
                        return null;
                    },
                    literal, literal, limit);
        } catch (Exception e) {
            log.warn("cosine retrieve failed, fallback ILIKE: {}", e.toString());
            return keywordSearch(query, limit);
        }
        return hits;
    }

    private List<RetrievalResult.Hit> keywordSearch(String query, int limit) {
        List<String> patterns = likePatterns(query);
        if (patterns.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT c.chunk_id::text AS chunk_id,
                       r.content AS content,
                       c.current_revision AS revision,
                       r.content_hash AS content_hash,
                       c.doc_id, c.collection, c.section, c.summary, c.chunk_key,
                       c.version_id, v.version, v.embedding_config_id,
                       v.embedding_model_version
                FROM kb_chunk c
                JOIN kb_document_version v ON v.id = c.version_id AND v.status = 'ACTIVE'
                JOIN kb_chunk_revision r
                  ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision AND r.status = 'ACTIVE'
                WHERE c.status = 'ACTIVE' AND (
                """);
        for (int i = 0; i < patterns.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("r.content ILIKE ?");
        }
        sql.append(") ORDER BY length(r.content) ASC LIMIT ?");
        List<Object> args = new ArrayList<>(patterns);
        args.add(limit);
        List<RetrievalResult.Hit> hits = new ArrayList<>();
        try {
            jdbcTemplate.query(sql.toString(),
                    rs -> {
                        while (rs.next()) {
                            hits.add(toHit(
                                    rs.getString("chunk_id"),
                                    rs.getString("content"),
                                    1.0,
                                    rs));
                        }
                        return null;
                    },
                    args.toArray());
        } catch (Exception e) {
            log.warn("knowledge retrieve failed: {}", e.toString());
            return List.of();
        }
        return hits;
    }

    private RetrievalResult.Hit toHit(String chunkId, String content, double score,
                                      java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("doc_id", rs.getString("doc_id"));
        meta.put("collection", rs.getString("collection"));
        meta.put("section", rs.getString("section"));
        meta.put("summary", rs.getString("summary"));
        meta.put("chunk_key", rs.getString("chunk_key"));
        meta.put("version_id", rs.getObject("version_id"));
        meta.put("version", rs.getString("version"));
        meta.put("revision", rs.getInt("revision"));
        meta.put("content_hash", rs.getString("content_hash"));
        meta.put("status", "ACTIVE");
        String configId = rs.getString("embedding_config_id");
        if (StringUtils.hasText(configId)) {
            meta.put("embedding_config_id", configId);
        }
        String modelVersion = rs.getString("embedding_model_version");
        if (StringUtils.hasText(modelVersion)) {
            meta.put("embedding_model_version", modelVersion);
        }
        return new RetrievalResult.Hit(chunkId, content, score, meta);
    }

    private static EmbeddingClient unavailableClient() {
        return new EmbeddingClient() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public float[] embed(String text) {
                return null;
            }
        };
    }
}
