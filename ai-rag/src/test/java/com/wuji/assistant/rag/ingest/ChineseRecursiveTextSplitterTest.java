package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
