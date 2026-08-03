package com.wuji.assistant.rag.ingest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识库文档查询。
 *
 * @author liudy
 */
@Service
public class KbDocumentQueryService {

    private final JdbcTemplate jdbcTemplate;

    public KbDocumentQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listDocuments(String collection) {
        if (collection == null || collection.isBlank()) {
            return jdbcTemplate.queryForList("""
                    SELECT doc_id, collection, title, current_version_id, create_time, update_time
                    FROM kb_document
                    ORDER BY update_time DESC
                    LIMIT 200
                    """);
        }
        return jdbcTemplate.queryForList("""
                SELECT doc_id, collection, title, current_version_id, create_time, update_time
                FROM kb_document
                WHERE collection = ?
                ORDER BY update_time DESC
                LIMIT 200
                """, collection);
    }

    public Map<String, Object> getDocument(String docId) {
        List<Map<String, Object>> docs = jdbcTemplate.queryForList("""
                SELECT doc_id, collection, title, current_version_id, create_time, update_time
                FROM kb_document WHERE doc_id = ?
                """, docId);
        if (docs.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> doc = docs.get(0);
        List<Map<String, Object>> versions = jdbcTemplate.queryForList("""
                SELECT id, version, status, source, published_at, deprecated_at, create_time
                FROM kb_document_version WHERE doc_id = ? ORDER BY create_time DESC
                """, docId);
        return Map.of(
                "document", doc,
                "versions", versions
        );
    }
}
