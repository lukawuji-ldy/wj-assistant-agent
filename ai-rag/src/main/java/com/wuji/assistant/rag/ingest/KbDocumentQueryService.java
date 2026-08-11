package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final ObjectMapper objectMapper;

    public KbDocumentQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 聊天侧兼容：最多 200 条。
     */
    public List<Map<String, Object>> listDocuments(String collection) {
        KbDocumentPage page = listDocuments(collection, null, 1, 200);
        return page.items();
    }

    /**
     * 管理台分页列表；可选按当前版本 status 过滤。
     *
     * @param collection 集合
     * @param status     ACTIVE|DEPRECATED；过滤 current_version 的 status
     * @param page       页码（1-based）
     * @param size       页大小
     * @return 分页
     */
    public KbDocumentPage listDocuments(String collection, String status, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        int offset = (p - 1) * s;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(collection)) {
            where.append(" AND d.collection = ? ");
            args.add(collection.trim());
        }
        if (StringUtils.hasText(status)) {
            where.append("""
                     AND EXISTS (
                       SELECT 1 FROM kb_document_version v
                       WHERE v.id = d.current_version_id AND v.status = ?
                     )
                    """);
            args.add(status.trim());
        }

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document d" + where,
                Integer.class,
                args.toArray());
        int totalCount = total == null ? 0 : total;

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(s);
        listArgs.add(offset);
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT d.doc_id, d.collection, d.title, d.current_version_id, d.create_time, d.update_time,
                       v.version AS current_version, v.status AS current_status
                FROM kb_document d
                LEFT JOIN kb_document_version v ON v.id = d.current_version_id
                """ + where + """
                ORDER BY d.update_time DESC
                LIMIT ? OFFSET ?
                """, listArgs.toArray());
        for (Map<String, Object> item : items) {
            stringifyLongIds(item, "current_version_id");
        }
        return new KbDocumentPage(items, totalCount, p, s);
    }

    /**
     * 已有 collection 去重列表（含空库时仍可由调用方补 default）。
     */
    public List<String> listCollections() {
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT collection FROM kb_document WHERE collection IS NOT NULL AND collection <> '' ORDER BY collection",
                String.class);
        return rows == null ? List.of() : rows;
    }

    public Map<String, Object> getDocument(String docId) {
        List<Map<String, Object>> docs = jdbcTemplate.queryForList("""
                SELECT doc_id, collection, title, current_version_id, create_time, update_time
                FROM kb_document WHERE doc_id = ?
                """, docId);
        if (docs.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> doc = new LinkedHashMap<>(docs.get(0));
        stringifyLongIds(doc, "current_version_id");
        // jsonb 以 text 取出，避免 JDBC PGobject 被 Jackson 序列化成 {type,value,null}
        List<Map<String, Object>> versions = jdbcTemplate.queryForList("""
                SELECT id, version, status, source,
                       acl_roles::text AS acl_roles,
                       ingest_options::text AS ingest_options,
                       published_at, deprecated_at, create_time, update_time
                FROM kb_document_version WHERE doc_id = ? ORDER BY create_time DESC
                """, docId);
        List<Map<String, Object>> versionViews = new ArrayList<>(versions.size());
        for (Map<String, Object> version : versions) {
            Map<String, Object> copy = new LinkedHashMap<>(version);
            stringifyLongIds(copy, "id");
            normalizeJsonField(copy, "acl_roles");
            normalizeJsonField(copy, "ingest_options");
            versionViews.add(copy);
        }
        Map<String, Object> out = new LinkedHashMap<>(2);
        out.put("document", doc);
        out.put("versions", versionViews);
        return out;
    }

    /**
     * 雪花 BIGINT 超过 JS Number.MAX_SAFE_INTEGER，JSON 数字会丢精度；管理台以字符串传 id。
     */
    static void stringifyLongIds(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object value = row.get(key);
            if (value instanceof Number) {
                row.put(key, String.valueOf(value));
            }
        }
    }

    /**
     * 将 jsonb 文本解析为 JSON 树（Map/List/标量），供 Web 层直接序列化。
     */
    void normalizeJsonField(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return;
        }
        Object value = row.get(key);
        if (value == null) {
            return;
        }
        if (value instanceof Map || value instanceof List) {
            return;
        }
        String json = value.toString().trim();
        if (!StringUtils.hasText(json) || "null".equalsIgnoreCase(json)) {
            row.put(key, null);
            return;
        }
        try {
            row.put(key, objectMapper.readValue(json, Object.class));
        } catch (Exception e) {
            // 保留原文，避免详情接口整体失败
            row.put(key, json);
        }
    }

    /**
     * 文档分页。
     */
    public record KbDocumentPage(List<Map<String, Object>> items, int total, int page, int size) {
    }
}
