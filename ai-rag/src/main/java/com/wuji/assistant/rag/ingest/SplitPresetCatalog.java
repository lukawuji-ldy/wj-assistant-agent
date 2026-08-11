package com.wuji.assistant.rag.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 切分预设目录（兼容旧 preset id）。
 * <p>
 * 新产品路径请用 {@link ContentTypeCatalog}；本类委托内容类型展开。
 * </p>
 *
 * @author liudy
 */
public final class SplitPresetCatalog {

    public static final String ZH_CHAPTER = ContentTypeCatalog.LEGACY_ZH_CHAPTER;
    public static final String MARKDOWN = ContentTypeCatalog.LEGACY_MARKDOWN;
    public static final String NUMERIC_OUTLINE = ContentTypeCatalog.LEGACY_NUMERIC;
    public static final String PLAIN_NARRATIVE = ContentTypeCatalog.LEGACY_PLAIN;
    public static final String CUSTOM = ContentTypeCatalog.CUSTOM;

    public static final String DEFAULT_CHAPTER_PATTERN =
            "(?m)^([一二三四五六七八九十百千]+、|第[一二三四五六七八九十百千\\d]+[章节篇]|\\d+(\\.\\d+)*\\s+)";

    public static final String MARKDOWN_CHAPTER_PATTERN = "(?m)^#{1,6}\\s+";
    public static final String NUMERIC_CHAPTER_PATTERN = "(?m)^\\d+(\\.\\d+)*\\s+";

    public static final List<String> DEFAULT_SEPARATORS =
            List.of("\n\n", "\n", "。", "！", "？", "；", "，");

    public static final List<String> DEFAULT_TRAILING_NOISE_MARKERS =
            List.of("复制上面这段");

    private SplitPresetCatalog() {
    }

    /**
     * 旧 API：返回内容类型列表（含旧 preset 别名说明时可只用 ContentTypeCatalog）。
     */
    public static List<Map<String, Object>> listPresets() {
        List<Map<String, Object>> content = ContentTypeCatalog.listContentTypes();
        List<Map<String, Object>> out = new ArrayList<>(content);
        // 兼容旧前端仍认 zh_chapter 等 id：附加别名项指向同一策略包
        out.add(aliasMeta(ZH_CHAPTER, "（兼容）中文章节", ContentTypeCatalog.NARRATIVE));
        out.add(aliasMeta(MARKDOWN, "（兼容）Markdown", ContentTypeCatalog.TECH_MARKDOWN));
        out.add(aliasMeta(NUMERIC_OUTLINE, "（兼容）数字大纲", ContentTypeCatalog.PRODUCT_MANUAL));
        out.add(aliasMeta(PLAIN_NARRATIVE, "（兼容）纯叙述", ContentTypeCatalog.PLAIN));
        return out;
    }

    public static PresetBundle expand(String presetId) {
        return ContentTypeCatalog.expand(presetId);
    }

    public static PresetBundle merge(String presetId, SplitOptions requestSplit, PreprocessOptions requestPre) {
        return ContentTypeCatalog.merge(presetId, requestSplit, requestPre);
    }

    public static String normalizePresetId(String presetId) {
        return ContentTypeCatalog.normalize(presetId);
    }

    private static Map<String, Object> aliasMeta(String id, String name, String targetContentType) {
        PresetBundle b = ContentTypeCatalog.expand(targetContentType);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("contentType", targetContentType);
        m.put("strategyId", targetContentType);
        m.put("name", name);
        m.put("description", "兼容旧 preset，等价于 contentType=" + targetContentType);
        m.put("deprecated", true);
        m.put("split", toSplitMap(b.split()));
        m.put("preprocess", toPreprocessMap(b.preprocess()));
        return m;
    }

    static Map<String, Object> toSplitMap(SplitOptions s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chunkSize", s.chunkSize());
        m.put("overlap", s.overlap());
        m.put("minChunkLengthToKeep", s.minChunkLengthToKeep());
        m.put("chapterSplitEnabled", s.chapterSplitEnabled());
        m.put("chapterPattern", s.chapterPattern());
        m.put("sectionTitleMode", s.sectionTitleMode() == null ? null : s.sectionTitleMode().name());
        m.put("separators", s.separators());
        m.put("keepSeparator", s.keepSeparator() == null ? null : s.keepSeparator().name());
        m.put("preset", s.preset());
        m.put("strategyId", s.preset());
        return m;
    }

    static Map<String, Object> toPreprocessMap(PreprocessOptions p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("normalizeNewlines", p.normalizeNewlines());
        m.put("stripPageNumbers", p.stripPageNumbers());
        m.put("mergeCjkHardWrap", p.mergeCjkHardWrap());
        m.put("collapseBlankLines", p.collapseBlankLines());
        m.put("trimOutsideChapters", p.trimOutsideChapters());
        m.put("trailingNoiseMarkers", p.trailingNoiseMarkers());
        m.put("chapterPattern", p.chapterPattern());
        return m;
    }

    /**
     * 预设展开结果。
     */
    public record PresetBundle(SplitOptions split, PreprocessOptions preprocess) {
    }
}
