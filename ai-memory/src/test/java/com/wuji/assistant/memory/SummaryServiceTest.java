package com.wuji.assistant.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.memory.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Summary 滚动合并规则单测。
 *
 * @author liudy
 */
class SummaryServiceTest {

    @Test
    void buildRollingSummary_mergesOldFacts() throws Exception {
        SummaryService svc = new SummaryService(null, null, new ObjectMapper());
        ChatMessage m = new ChatMessage();
        m.setRole("user");
        m.setContent("继续开发 Memory 模块");
        String json = svc.buildRollingSummary(
                "{\"goal\":\"开发系统\",\"facts\":[\"Java 17\"],\"pending_tasks\":[]}",
                List.of(m));
        @SuppressWarnings("unchecked")
        Map<String, Object> map = new ObjectMapper().readValue(json, Map.class);
        assertTrue(String.valueOf(map.get("goal")).contains("开发"));
        assertTrue(String.valueOf(map.get("facts")).contains("Java 17"));
    }

    @Test
    void buildRollingSummary_stripsNulBytes() throws Exception {
        SummaryService svc = new SummaryService(null, null, new ObjectMapper());
        ChatMessage m = new ChatMessage();
        m.setRole("assistant");
        m.setContent("</mm:think>\u00001+1=2");
        String json = svc.buildRollingSummary(null, List.of(m));
        assertFalse(json.contains("\\u0000"), json);
        assertFalse(json.indexOf('\u0000') >= 0, json);
        assertTrue(json.contains("1+1=2"), json);
    }

    @Test
    void buildRollingSummary_stripsNulFromOldSummaryFacts() throws Exception {
        SummaryService svc = new SummaryService(null, null, new ObjectMapper());
        // 模拟历史摘要中已含 Jackson 风格 \u0000 转义
        String old = "{\"goal\":\"g\",\"facts\":[\"assistant: x\\\\u0000y\"],\"pending_tasks\":[]}";
        String json = svc.buildRollingSummary(old, List.of());
        assertFalse(json.contains("\\u0000"), json);
    }
}
