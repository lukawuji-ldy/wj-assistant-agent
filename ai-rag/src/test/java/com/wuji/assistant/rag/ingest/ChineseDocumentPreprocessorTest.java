package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 预处理器单测。
 *
 * @author liudy
 */
class ChineseDocumentPreprocessorTest {

    @Test
    void mergesCjkHardWrapAndCollapsesBlankLines() {
        ChineseDocumentPreprocessor p = new ChineseDocumentPreprocessor();
        String out = p.preprocess("中文\n测试\n\n\n下一行");
        assertFalse(out.contains("中文\n测"));
        assertEquals(-1, out.indexOf("\n\n\n"));
    }
}
