package com.wuji.assistant.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索（MVP：关键词召回；有向量时可扩展余弦检索）。
 *
 * @author liudy
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeRetrievalService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 检索知识片段。
     *
     * @param query              查询
     * @param topK               条数
     * @param minReliableScore   最低可靠分（关键词命中按 1.0）
     * @return 结果
     */
    public RetrievalResult retrieve(String query, int topK, double minReliableScore) {
        if (!StringUtils.hasText(query)) {
            return new RetrievalResult(List.of(), true, "查询为空");
        }
        int limit = Math.max(1, topK);
        String like = "%" + query.trim() + "%";
        List<RetrievalResult.Hit> hits = new ArrayList<>();
        try {
            jdbcTemplate.query("""
                            SELECT id::text AS id, content, metadata::text AS metadata
                            FROM vector_store
                            WHERE content ILIKE ?
                            ORDER BY length(content) ASC
                            LIMIT ?
                            """,
                    rs -> {
                        while (rs.next()) {
                            Map<String, Object> meta = parseMeta(rs.getString("metadata"));
                            hits.add(new RetrievalResult.Hit(
                                    rs.getString("id"),
                                    rs.getString("content"),
                                    1.0,
                                    meta
                            ));
                        }
                        return null;
                    },
                    like, limit);
        } catch (Exception e) {
            log.warn("knowledge retrieve failed: {}", e.toString());
            return new RetrievalResult(List.of(), true, "知识库暂不可用");
        }
        List<RetrievalResult.Hit> reliable = hits.stream()
                .filter(h -> h.score() >= minReliableScore)
                .toList();
        if (reliable.isEmpty()) {
            return new RetrievalResult(List.of(), true, "无可靠知识命中");
        }
        return new RetrievalResult(reliable, false, null);
    }

    private Map<String, Object> parseMeta(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
