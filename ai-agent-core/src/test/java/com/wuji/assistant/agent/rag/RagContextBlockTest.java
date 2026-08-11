package com.wuji.assistant.agent.rag;

import com.wuji.assistant.rag.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预检索块格式。
 *
 * @author liudy
 */
class RagContextBlockTest {

    @Test
    void formatHitsIncludesContentAndForbidsRefuse() {
        RetrievalResult.Hit hit = new RetrievalResult.Hit(
                "c1",
                "五、孤身入北莽，伪境白头。第三次出北凉是孤身闯敌国。",
                1.0,
                Map.of("doc_id", "doc_xu", "section", "五、孤身入北莽"));
        String block = RagContextBlock.format(new RetrievalResult(List.of(hit), false, null));
        assertTrue(block.contains(RagContextBlock.HITS_HEADING), block);
        assertTrue(block.contains("孤身入北莽"), block);
        assertTrue(block.contains("禁止说"), block);
        assertTrue(block.contains("doc_xu"), block);
    }

    @Test
    void formatRejectedRemindsToCallTool() {
        String block = RagContextBlock.format(new RetrievalResult(List.of(), true, "无可靠知识命中"));
        assertTrue(block.contains(RagContextBlock.MISS_HEADING), block);
        assertTrue(block.contains("knowledge_retrieval"), block);
        assertFalse(block.contains(RagContextBlock.HITS_HEADING), block);
    }
}
