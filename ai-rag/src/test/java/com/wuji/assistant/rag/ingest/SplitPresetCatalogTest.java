package com.wuji.assistant.rag.ingest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧 preset 目录委托单测。
 *
 * @author liudy
 */
class SplitPresetCatalogTest {

    @Test
    void expandDelegatesToContentType() {
        SplitPresetCatalog.PresetBundle b = SplitPresetCatalog.expand(SplitPresetCatalog.ZH_CHAPTER);
        assertEquals(ContentTypeCatalog.NARRATIVE, b.split().preset());
        assertTrue(Boolean.TRUE.equals(b.preprocess().trimOutsideChapters()));
    }

    @Test
    void listPresetsIncludesContentTypesAndAliases() {
        assertTrue(SplitPresetCatalog.listPresets().size() >= 8);
        assertTrue(SplitPresetCatalog.listPresets().stream().anyMatch(m -> "narrative".equals(m.get("id"))));
        assertTrue(SplitPresetCatalog.listPresets().stream().anyMatch(m -> "zh_chapter".equals(m.get("id"))));
    }

    @Test
    void mergeLegacyId() {
        SplitOptions req = new SplitOptions(200, 20, 10, true, null, null, null, null, "zh_chapter");
        SplitPresetCatalog.PresetBundle merged = SplitPresetCatalog.merge("zh_chapter", req, null);
        assertEquals(200, merged.split().chunkSize());
        assertEquals(ContentTypeCatalog.NARRATIVE, merged.split().preset());
    }

    @Test
    void policyPatternMatchesChapterArticleFaqAndBannerPrefix() {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(ContentTypeCatalog.POLICY_CHAPTER_PATTERN);
        assertTrue(p.matcher("第一章 合同基本信息\n").find());
        assertTrue(p.matcher("第一条 服务内容\n").find());
        assertTrue(p.matcher("============================== 第三章 合同费用与支付\n").find());
        assertTrue(p.matcher("Q：合同金额是多少？\n").find());
        assertTrue(ContentTypeCatalog.listContentTypes().stream()
                .filter(m -> ContentTypeCatalog.POLICY_CLAUSE.equals(m.get("id")))
                .anyMatch(m -> String.valueOf(m.get("description")).contains("第X章")));
    }
}
