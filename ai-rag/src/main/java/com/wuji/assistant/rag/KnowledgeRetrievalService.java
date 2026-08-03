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
import java.util.List;
import java.util.Map;

/**
 * 知识库检索：优先余弦；无 Embedding 时降级 ILIKE。
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

    /**
     * 检索知识片段。
     *
     * @param query            查询
     * @param topK             条数
     * @param minReliableScore 最低可靠分
     * @return 结果
     */
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
                            SELECT id::text AS id, content, metadata::text AS metadata,
                                   (1 - (embedding <=> ?::vector)) AS score
                            FROM vector_store
                            WHERE embedding IS NOT NULL
                              AND COALESCE(metadata ->> 'status', 'ACTIVE') = 'ACTIVE'
                            ORDER BY embedding <=> ?::vector
                            LIMIT ?
                            """,
                    rs -> {
                        while (rs.next()) {
                            hits.add(new RetrievalResult.Hit(
                                    rs.getString("id"),
                                    rs.getString("content"),
                                    rs.getDouble("score"),
                                    parseMeta(rs.getString("metadata"))
                            ));
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
                            SELECT id::text AS id, content, metadata::text AS metadata
                            FROM vector_store
                            WHERE content ILIKE ?
                              AND COALESCE(metadata ->> 'status', 'ACTIVE') = 'ACTIVE'
                            ORDER BY length(content) ASC
                            LIMIT ?
                            """,
                    rs -> {
                        while (rs.next()) {
                            hits.add(new RetrievalResult.Hit(
                                    rs.getString("id"),
                                    rs.getString("content"),
                                    1.0,
                                    parseMeta(rs.getString("metadata"))
                            ));
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
