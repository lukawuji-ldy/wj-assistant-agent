package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容类型目录单测。
 *
 * @author liudy
 */
class ContentTypeCatalogTest {

    @Test
    void legacyPresetMapsToContentType() {
        assertEquals(ContentTypeCatalog.NARRATIVE, ContentTypeCatalog.normalize("zh_chapter"));
        assertEquals(ContentTypeCatalog.TECH_MARKDOWN, ContentTypeCatalog.normalize("markdown"));
        assertEquals(ContentTypeCatalog.PLAIN, ContentTypeCatalog.normalize("plain_narrative"));
        assertEquals(ContentTypeCatalog.PRODUCT_MANUAL, ContentTypeCatalog.normalize("numeric_outline"));
    }

    @Test
    void faqUsesQaBoundaryPattern() {
        SplitPresetCatalog.PresetBundle b = ContentTypeCatalog.expand(ContentTypeCatalog.FAQ_QA);
        assertTrue(Boolean.TRUE.equals(b.split().chapterSplitEnabled()));
        assertEquals(ContentTypeCatalog.FAQ_CHAPTER_PATTERN, b.split().chapterPattern());
        assertEquals(0, b.split().overlap());
    }

    @Test
    void policyUsesClausePattern() {
        SplitPresetCatalog.PresetBundle b = ContentTypeCatalog.expand(ContentTypeCatalog.POLICY_CLAUSE);
        assertTrue(b.split().chapterPattern().contains("条"));
        assertTrue(b.split().chapterPattern().contains("章"));
    }

    @Test
    void listContentTypesHasEight() {
        assertEquals(8, ContentTypeCatalog.listContentTypes().size());
    }

    @Test
    void faqSplitsByQuestionMarker() {
        ChineseRecursiveTextSplitter splitter = new ChineseRecursiveTextSplitter();
        String text = """
                Q: 如何改密？
                A: 进入个人中心。

                问：如何注销？
                答：提交工单。
                """;
        SplitPresetCatalog.PresetBundle b = ContentTypeCatalog.expand(ContentTypeCatalog.FAQ_QA);
        List<TextSplitter.TextChunk> chunks = splitter.split(text, b.split());
        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.stream().anyMatch(c -> c.section().contains("改密") || c.content().contains("改密")));
    }

    @Test
    void requestOverrideKeepsContentType() {
        SplitOptions req = new SplitOptions(300, 10, 10, true, null, null, null, null, ContentTypeCatalog.NARRATIVE);
        SplitPresetCatalog.PresetBundle merged = ContentTypeCatalog.merge(ContentTypeCatalog.NARRATIVE, req, null);
        assertEquals(300, merged.split().chunkSize());
        assertEquals(ContentTypeCatalog.NARRATIVE, merged.split().preset());
    }

    @Test
    void suggestByFilename() {
        assertEquals(ContentTypeCatalog.TECH_MARKDOWN, ContentTypeCatalog.suggestByFilename("a.md"));
        assertEquals(ContentTypeCatalog.CODE_STRUCTURE, ContentTypeCatalog.suggestByFilename("Foo.java"));
        assertFalse(ContentTypeCatalog.suggestByFilename("x.txt").isBlank());
    }
}
