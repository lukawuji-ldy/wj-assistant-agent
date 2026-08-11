package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void trimOutsideChaptersAndTrailingNoise() {
        ChineseDocumentPreprocessor p = new ChineseDocumentPreprocessor();
        String raw = """
                聊天套话开头

                一、第一章
                正文内容。

                复制上面这段存档。
                """;
        PreprocessOptions opts = new PreprocessOptions(
                true, true, true, true, true,
                List.of("复制上面这段"),
                SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN);
        String out = p.preprocess(raw, opts);
        assertTrue(out.startsWith("一、"));
        assertFalse(out.contains("聊天套话"));
        assertFalse(out.contains("复制上面这段"));
    }

    @Test
    void unwrapsDecorativeBannersAndKeepsTitle() {
        ChineseDocumentPreprocessor p = new ChineseDocumentPreprocessor();
        String raw = """
                前言

                ============================== 第三章 合同费用与支付
                ==============================

                第三条 服务费用
                """;
        String out = p.preprocess(raw);
        assertTrue(out.contains("第三章 合同费用与支付"));
        assertFalse(out.contains("===="));
        assertTrue(out.contains("第三条 服务费用"));
    }

    @Test
    void doesNotMergeCjkWrapAcrossChapterOrArticleHeadings() {
        ChineseDocumentPreprocessor p = new ChineseDocumentPreprocessor();
        String raw = """
                第一章 合同基本信息
                合同名称：技术服务合同
                第一条 服务内容
                乙方提供维护。
                """;
        String out = p.preprocess(raw);
        assertTrue(out.contains("第一章 合同基本信息\n"), out);
        assertTrue(out.contains("\n第一条 服务内容"), out);
        assertFalse(out.contains("信息合同名称"), out);
        assertFalse(out.contains("内容乙方"), out);
    }

    @Test
    void doesNotStripShortMarkdownSetextUnderline() {
        ChineseDocumentPreprocessor p = new ChineseDocumentPreprocessor();
        String raw = "标题\n===\n正文";
        String out = p.preprocess(raw);
        assertTrue(out.contains("==="));
    }
}
