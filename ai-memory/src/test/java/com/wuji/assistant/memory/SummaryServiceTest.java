package com.wuji.assistant.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.memory.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
}
