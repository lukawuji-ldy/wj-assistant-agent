package com.wuji.assistant.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索：优先余弦（kb_chunk.embedding）；无 Embedding 时降级 ILIKE。
 *
 * @author liudy
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingClient embeddingClient;

    public KnowledgeRetrievalService(JdbcTemplate jdbcTemplate,
                                     ObjectMapper objectMapper,
                                     ObjectProvider<EmbeddingClient> embeddingClients) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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

    public RetrievalResult retrieve(String query, int topK, double minReliableScore) {
        if (!StringUtils.hasText(query)) {
            return new RetrievalResult(List.of(), true, "查询为空");
        }
        int limit = Math.max(1, topK);
        List<RetrievalResult.Hit> hits;
        if (embeddingClient.available()) {
            hits = cosineSearch(query.trim(), limit);
        } else {
            log.debug("embedding unavailable, fallback to ILIKE retrieval");
            hits = keywordSearch(query.trim(), limit);
        }
        List<RetrievalResult.Hit> reliable = hits.stream()
                .filter(h -> h.score() >= minReliableScore)
                .toList();
        if (reliable.isEmpty()) {
            return new RetrievalResult(List.of(), true, "无可靠知识命中");
        }
        return new RetrievalResult(reliable, false, null);
    }

    private List<RetrievalResult.Hit> cosineSearch(String query, int limit) {
        float[] vector = embeddingClient.embed(query);
        if (vector == null) {
            return keywordSearch(query, limit);
        }
        String literal = toVectorLiteral(vector);
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
        String like = "%" + query + "%";
        List<RetrievalResult.Hit> hits = new ArrayList<>();
        try {
            jdbcTemplate.query("""
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
                            WHERE c.status = 'ACTIVE'
                              AND r.content ILIKE ?
                            ORDER BY length(r.content) ASC
                            LIMIT ?
                            """,
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
                    like, limit);
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

    @SuppressWarnings("unused")
    private Map<String, Object> parseMeta(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String toVectorLiteral(float[] vector) {
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
