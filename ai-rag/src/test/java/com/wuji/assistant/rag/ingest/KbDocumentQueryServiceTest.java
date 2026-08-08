package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 知识库查询：BIGINT id 字符串化；jsonb 解析避免 PGobject 外壳。
 *
 * @author liudy
 */
class KbDocumentQueryServiceTest {

    private final KbDocumentQueryService service =
            new KbDocumentQueryService(null, new ObjectMapper());

    @Test
    void stringifyLongIds_convertsSnowflakeToExactString() {
        long snowflake = (System.currentTimeMillis() << 20) | 42L;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", snowflake);
        row.put("version", "v2");

        KbDocumentQueryService.stringifyLongIds(row, "id");

        assertInstanceOf(String.class, row.get("id"));
        assertEquals(Long.toString(snowflake), row.get("id"));
        assertEquals("v2", row.get("version"));
    }

    @Test
    void normalizeJsonField_parsesIngestOptionsObject() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ingest_options",
                "{\"parser\":\"plaintext\",\"chunkSize\":500,\"chapterSplitEnabled\":true}");

        service.normalizeJsonField(row, "ingest_options");

        assertInstanceOf(Map.class, row.get("ingest_options"));
        @SuppressWarnings("unchecked")
        Map<String, Object> opts = (Map<String, Object>) row.get("ingest_options");
        assertEquals("plaintext", opts.get("parser"));
        assertEquals(500, opts.get("chunkSize"));
        assertEquals(true, opts.get("chapterSplitEnabled"));
    }

    @Test
    void normalizeJsonField_parsesAclRolesArray() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("acl_roles", "[\"admin\",\"user\"]");

        service.normalizeJsonField(row, "acl_roles");

        assertInstanceOf(List.class, row.get("acl_roles"));
        assertEquals(List.of("admin", "user"), row.get("acl_roles"));
    }

    @Test
    void normalizeJsonField_nullLiteralBecomesNull() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ingest_options", "null");

        service.normalizeJsonField(row, "ingest_options");

        assertNull(row.get("ingest_options"));
    }
}
