package com.wuji.assistant.server.admin.audit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AdminAuditDetail 单元测试。
 *
 * @author liudy
 */
class AdminAuditDetailTest {

    @Test
    void changeRecordsFromToAndSkipsEquals() {
        Map<String, Object> detail = AdminAuditDetail.builder()
                .change("model", "MiniMax-M2.5", "MiniMax-M3")
                .change("status", "ACTIVE", "ACTIVE")
                .build();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detail.get("changes");
        assertEquals(1, changes.size());
        assertEquals("model", changes.get(0).get("field"));
        assertEquals("MiniMax-M2.5", changes.get(0).get("from"));
        assertEquals("MiniMax-M3", changes.get(0).get("to"));
        assertFalse(detail.containsKey("meta"));
    }

    @Test
    void createdUsesNullFrom() {
        Map<String, Object> detail = AdminAuditDetail.builder()
                .created("modelKind", "CHAT")
                .sensitiveChanged("apiKey")
                .meta("activate", true)
                .build();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detail.get("changes");
        assertEquals(2, changes.size());
        assertNull(changes.get(0).get("from"));
        assertEquals("CHAT", changes.get(0).get("to"));
        assertEquals("apiKey", changes.get(1).get("field"));
        assertEquals(AdminAuditDetail.CHANGED, changes.get(1).get("to"));
        assertEquals(true, ((Map<?, ?>) detail.get("meta")).get("activate"));
    }

    @Test
    void emptyChangesAllowedWithMeta() {
        Map<String, Object> detail = AdminAuditDetail.builder()
                .meta("referenced", false)
                .build();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detail.get("changes");
        assertTrue(changes.isEmpty());
        assertEquals(false, ((Map<?, ?>) detail.get("meta")).get("referenced"));
    }

    @Test
    void changeSkipsNumericallyEqualTemperatureDespiteScale() {
        Map<String, Object> detail = AdminAuditDetail.builder()
                .change("temperature", new BigDecimal("0.70"), new BigDecimal("0.7"))
                .change("maxTokens", 4096, 32768)
                .build();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) detail.get("changes");
        assertEquals(1, changes.size());
        assertEquals("maxTokens", changes.get(0).get("field"));
        assertEquals(4096, changes.get(0).get("from"));
        assertEquals(32768, changes.get(0).get("to"));
    }

    @Test
    void sameValueTreatsCrossNumberTypesAsEqual() {
        assertTrue(AdminAuditDetail.sameValue(new BigDecimal("0.7"), 0.7d));
        assertTrue(AdminAuditDetail.sameValue(4096, 4096L));
        assertFalse(AdminAuditDetail.sameValue(new BigDecimal("0.7"), new BigDecimal("0.8")));
    }
}
