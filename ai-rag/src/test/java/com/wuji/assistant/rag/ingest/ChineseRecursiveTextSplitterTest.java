package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中文切分器单测。
 *
 * @author liudy
 */
class ChineseRecursiveTextSplitterTest {

    @Test
    void splitsByChapterAndSize() {
        ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
        splitter.setChunkSize(40);
        splitter.setOverlap(5);
        splitter.setMinChunkLengthToKeep(10);
        String text = """
                一、总则
                本章规定请假流程与审批权限，适用于全体正式员工。
                二、年假
                员工每年可享受十五天年假，需提前三天在 HR 门户提交申请。
                """;
        List<TextSplitter.TextChunk> chunks = splitter.split(text);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().anyMatch(c -> c.section() != null && c.section().contains("一")));
    }

    @Test
    void fullLineSectionTitle() {
        ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
        splitter.setSectionTitleMode(SectionTitleMode.FULL_LINE);
        String text = """
                一、北凉世子
                正文一段。
                """;
        List<TextSplitter.TextChunk> chunks = splitter.split(text);
        assertTrue(chunks.stream().anyMatch(c -> "一、北凉世子".equals(c.section())));
    }

    @Test
    void matchModeKeepsPrefixOnly() {
        ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
        String text = """
                一、北凉世子
                正文一段。
                """;
        List<TextSplitter.TextChunk> chunks = splitter.split(text,
                new SplitOptions(500, 0, 10, true, null, SectionTitleMode.MATCH, null, null, null));
        assertTrue(chunks.stream().anyMatch(c -> "一、".equals(c.section())));
    }

    @Test
    void splitOptionsDoNotMutateSharedDefaults() {
        ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
        splitter.setChunkSize(500);
        splitter.setOverlap(80);
        String longText = ("段落内容。").repeat(80);
        List<TextSplitter.TextChunk> fine = splitter.split(longText, new SplitOptions(40, 0, 5, false));
        List<TextSplitter.TextChunk> coarse = splitter.split(longText, new SplitOptions(400, 0, 5, false));
        int fineMax = fine.stream().mapToInt(c -> c.content().length()).max().orElse(0);
        int coarseMax = coarse.stream().mapToInt(c -> c.content().length()).max().orElse(0);
        assertTrue(fineMax <= 80, "fine max=" + fineMax);
        assertTrue(coarseMax > fineMax, "coarseMax=" + coarseMax + " fineMax=" + fineMax);
        assertEquals(500, splitter.getChunkSize());
        assertEquals(80, splitter.getOverlap());
        SplitOptions resolved = splitter.resolve(new SplitOptions(120, null, null, false));
        assertEquals(120, resolved.chunkSize());
        assertEquals(80, resolved.overlap());
        assertEquals(false, resolved.chapterSplitEnabled());
    }

    @Test
    void rejectsOverlapNotLessThanChunkSize() {
        ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
        assertThrows(IllegalArgumentException.class, () ->
                splitter.split("一段足够长的中文内容用于触发校验。".repeat(20),
                        new SplitOptions(50, 50, 10, false)));
    }

    @Test
    void policyHierarchySkipsEmptyChapterAndPrefixesArticleSection() {
        ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
        String text = """
                第一章 合同基本信息
                合同名称：技术服务合同。
                第二章 服务范围
                第一条 服务内容
                乙方提供开发与维护。
                第二条 服务标准
                按约定时间完成。
                第三章 合同费用与支付
                第三条 服务费用
                合同总金额人民币100万元。
                """;
        List<TextSplitter.TextChunk> chunks = splitter.split(text, new SplitOptions(
                1500, 0, 10, true,
                ContentTypeCatalog.POLICY_CHAPTER_PATTERN,
                SectionTitleMode.FULL_LINE, null, KeepSeparator.APPEND,
                ContentTypeCatalog.POLICY_CLAUSE));
        assertTrue(chunks.stream().anyMatch(c ->
                "第一章 合同基本信息".equals(c.section()) && c.content().contains("合同名称")));
        assertTrue(chunks.stream().noneMatch(c -> "第二章 服务范围".equals(c.section())));
        assertTrue(chunks.stream().noneMatch(c -> c.section() != null && c.section().contains("第二条")
                && c.content().contains("第三章")));
        assertTrue(chunks.stream().anyMatch(c ->
                c.section() != null
                        && c.section().contains("第三章")
                        && c.section().contains("第三条")
                        && c.content().contains("100万")));
    }

    @Test
    void policyPeelsAppendixHeadingIntoFollowingFaq() {
        ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
        String text = """
                第九条 争议处理
                双方发生争议时，应首先协商解决。
                法律合同FAQ抽取
                Q：合同金额是多少？
                A：人民币100万元。
                """;
        List<TextSplitter.TextChunk> chunks = splitter.split(text, new SplitOptions(
                1500, 0, 10, true,
                ContentTypeCatalog.POLICY_CHAPTER_PATTERN,
                SectionTitleMode.FULL_LINE, null, KeepSeparator.APPEND,
                ContentTypeCatalog.POLICY_CLAUSE));
        assertTrue(chunks.stream().anyMatch(c ->
                c.section() != null && c.section().contains("第九条") && !c.content().contains("Q：")));
        assertTrue(chunks.stream().anyMatch(c ->
                c.content().contains("Q：合同金额") && c.content().contains("法律合同FAQ抽取")));
    }
}
