package com.wuji.assistant.rag.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档内容类型（产品层）→ 策略包（技术层）。
 * <p>
 * 知识集合（collection）不绑定策略；策略仅在文档/版本入库时选择。
 * </p>
 *
 * @author liudy
 */
public final class ContentTypeCatalog {

    public static final String PRODUCT_MANUAL = "product_manual";
    public static final String FAQ_QA = "faq_qa";
    public static final String POLICY_CLAUSE = "policy_clause";
    public static final String TECH_MARKDOWN = "tech_markdown";
    public static final String NARRATIVE = "narrative";
    public static final String PLAIN = "plain";
    public static final String CODE_STRUCTURE = "code_structure";
    public static final String CUSTOM = "custom";

    /** 兼容旧 preset id。 */
    public static final String LEGACY_ZH_CHAPTER = "zh_chapter";
    public static final String LEGACY_MARKDOWN = "markdown";
    public static final String LEGACY_NUMERIC = "numeric_outline";
    public static final String LEGACY_PLAIN = "plain_narrative";

    public static final String FAQ_CHAPTER_PATTERN =
            "(?m)^(?:Q\\s*[:：]|问\\s*[:：]|【问】)";
    public static final String POLICY_CHAPTER_PATTERN =
            "(?m)^(?:[-=\u2014]{3,}\\s*)?第[一二三四五六七八九十百千零〇两\\d]+[章条]|"
                    + FAQ_CHAPTER_PATTERN;
    public static final String CODE_SIGNATURE_PATTERN =
            "(?m)^(?:public |private |protected |internal |class |interface |enum |fun |def |function |fn )";

    private static final Set<String> KNOWN = Set.of(
            PRODUCT_MANUAL, FAQ_QA, POLICY_CLAUSE, TECH_MARKDOWN, NARRATIVE, PLAIN, CODE_STRUCTURE, CUSTOM,
            LEGACY_ZH_CHAPTER, LEGACY_MARKDOWN, LEGACY_NUMERIC, LEGACY_PLAIN);

    private ContentTypeCatalog() {
    }

    /**
     * 供 Admin UI：内容类型列表（产品名）。
     */
    public static List<Map<String, Object>> listContentTypes() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(meta(PRODUCT_MANUAL, "产品说明书",
                "按章节/节号硬切，块偏大（约 800～1000 字）；适合流程与功能说明"));
        out.add(meta(FAQ_QA, "FAQ 问答",
                "按 Q/问 边界切分，一对问答尽量一块；勿用固定长度横切"));
        out.add(meta(POLICY_CLAUSE, "制度/法律条款",
                "按「第X章/第X条」两级硬切，禁止跨条合并"));
        out.add(meta(TECH_MARKDOWN, "技术文档",
                "按 Markdown 标题 # / ## 硬切"));
        out.add(meta(NARRATIVE, "叙事/传记",
                "按中文章节「一、」「第N章」硬切；如人物经历"));
        out.add(meta(PLAIN, "通用正文",
                "无结构时按块大小与句读递归切分"));
        out.add(meta(CODE_STRUCTURE, "代码知识（占位）",
                "启发式按签名行/空行切分；完整 AST 后续期"));
        out.add(meta(CUSTOM, "自定义",
                "专家模式：展开全部技术参数"));
        return out;
    }

    /**
     * 旧 preset / 新 contentType → 权威 contentTypeId。
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return NARRATIVE;
        }
        String id = raw.trim();
        return switch (id) {
            case LEGACY_ZH_CHAPTER -> NARRATIVE;
            case LEGACY_MARKDOWN -> TECH_MARKDOWN;
            case LEGACY_PLAIN -> PLAIN;
            case LEGACY_NUMERIC -> PRODUCT_MANUAL;
            default -> KNOWN.contains(id) ? id : NARRATIVE;
        };
    }

    /**
     * 是否为已知策略 id（含旧 preset）。
     */
    public static boolean isKnown(String raw) {
        return raw != null && KNOWN.contains(raw.trim());
    }

    /**
     * 展开为策略包；strategyId 写入 SplitOptions.preset。
     */
    public static SplitPresetCatalog.PresetBundle expand(String contentTypeOrPreset) {
        String id = normalize(contentTypeOrPreset);
        // numeric_outline 仍可作为显式旧 id 展开（normalize 会变 product_manual；保留分支供 merge 前）
        if (LEGACY_NUMERIC.equals(contentTypeOrPreset == null ? "" : contentTypeOrPreset.trim())) {
            return bundle(PRODUCT_MANUAL,
                    new SplitOptions(900, 100, 50, true,
                            SplitPresetCatalog.NUMERIC_CHAPTER_PATTERN, SectionTitleMode.FULL_LINE,
                            SplitPresetCatalog.DEFAULT_SEPARATORS, KeepSeparator.APPEND, PRODUCT_MANUAL),
                    clean(true, SplitPresetCatalog.NUMERIC_CHAPTER_PATTERN));
        }
        return switch (id) {
            case PRODUCT_MANUAL -> bundle(PRODUCT_MANUAL,
                    new SplitOptions(900, 100, 50, true,
                            SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN, SectionTitleMode.FULL_LINE,
                            SplitPresetCatalog.DEFAULT_SEPARATORS, KeepSeparator.APPEND, PRODUCT_MANUAL),
                    clean(true, SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN));
            case FAQ_QA -> bundle(FAQ_QA,
                    new SplitOptions(2000, 0, 20, true,
                            FAQ_CHAPTER_PATTERN, SectionTitleMode.FULL_LINE,
                            List.of("\n\n", "\n"), KeepSeparator.APPEND, FAQ_QA),
                    clean(false, FAQ_CHAPTER_PATTERN));
            case POLICY_CLAUSE -> bundle(POLICY_CLAUSE,
                    new SplitOptions(1500, 0, 30, true,
                            POLICY_CHAPTER_PATTERN, SectionTitleMode.FULL_LINE,
                            SplitPresetCatalog.DEFAULT_SEPARATORS, KeepSeparator.APPEND, POLICY_CLAUSE),
                    clean(true, POLICY_CHAPTER_PATTERN));
            case TECH_MARKDOWN -> bundle(TECH_MARKDOWN,
                    new SplitOptions(500, 80, 50, true,
                            SplitPresetCatalog.MARKDOWN_CHAPTER_PATTERN, SectionTitleMode.FULL_LINE,
                            SplitPresetCatalog.DEFAULT_SEPARATORS, KeepSeparator.APPEND, TECH_MARKDOWN),
                    clean(true, SplitPresetCatalog.MARKDOWN_CHAPTER_PATTERN));
            case PLAIN -> bundle(PLAIN,
                    new SplitOptions(500, 80, 50, false,
                            SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN, SectionTitleMode.FULL_LINE,
                            SplitPresetCatalog.DEFAULT_SEPARATORS, KeepSeparator.APPEND, PLAIN),
                    clean(false, SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN));
            case CODE_STRUCTURE -> bundle(CODE_STRUCTURE,
                    new SplitOptions(800, 40, 40, true,
                            CODE_SIGNATURE_PATTERN, SectionTitleMode.FULL_LINE,
                            List.of("\n\n", "\n"), KeepSeparator.APPEND, CODE_STRUCTURE),
                    clean(false, CODE_SIGNATURE_PATTERN));
            case CUSTOM -> bundle(CUSTOM,
                    new SplitOptions(500, 80, 50, true,
                            SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN, SectionTitleMode.FULL_LINE,
                            SplitPresetCatalog.DEFAULT_SEPARATORS, KeepSeparator.APPEND, CUSTOM),
                    clean(false, SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN));
            default -> bundle(NARRATIVE,
                    new SplitOptions(500, 80, 50, true,
                            SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN, SectionTitleMode.FULL_LINE,
                            SplitPresetCatalog.DEFAULT_SEPARATORS, KeepSeparator.APPEND, NARRATIVE),
                    clean(true, SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN));
        };
    }

    /**
     * 合并请求覆盖；contentTypeOrPreset 优先于 split.preset。
     */
    public static SplitPresetCatalog.PresetBundle merge(String contentTypeOrPreset,
                                                        SplitOptions requestSplit,
                                                        PreprocessOptions requestPre) {
        String raw = contentTypeOrPreset;
        if ((raw == null || raw.isBlank()) && requestSplit != null && requestSplit.preset() != null) {
            raw = requestSplit.preset();
        }
        String normalized = normalize(raw);
        SplitPresetCatalog.PresetBundle base = expand(raw == null || raw.isBlank() ? normalized : raw);
        // 若传入旧 numeric_outline，expand 已处理；normalize 后 product_manual
        SplitOptions s = base.split();
        PreprocessOptions p = base.preprocess();
        if (requestSplit != null) {
            s = new SplitOptions(
                    coalesce(requestSplit.chunkSize(), s.chunkSize()),
                    coalesce(requestSplit.overlap(), s.overlap()),
                    coalesce(requestSplit.minChunkLengthToKeep(), s.minChunkLengthToKeep()),
                    coalesce(requestSplit.chapterSplitEnabled(), s.chapterSplitEnabled()),
                    coalesce(requestSplit.chapterPattern(), s.chapterPattern()),
                    coalesce(requestSplit.sectionTitleMode(), s.sectionTitleMode()),
                    requestSplit.separators() != null ? requestSplit.separators() : s.separators(),
                    coalesce(requestSplit.keepSeparator(), s.keepSeparator()),
                    normalized);
        } else {
            s = new SplitOptions(
                    s.chunkSize(), s.overlap(), s.minChunkLengthToKeep(), s.chapterSplitEnabled(),
                    s.chapterPattern(), s.sectionTitleMode(), s.separators(), s.keepSeparator(),
                    normalized);
        }
        if (requestPre != null) {
            p = new PreprocessOptions(
                    coalesce(requestPre.normalizeNewlines(), p.normalizeNewlines()),
                    coalesce(requestPre.stripPageNumbers(), p.stripPageNumbers()),
                    coalesce(requestPre.mergeCjkHardWrap(), p.mergeCjkHardWrap()),
                    coalesce(requestPre.collapseBlankLines(), p.collapseBlankLines()),
                    coalesce(requestPre.trimOutsideChapters(), p.trimOutsideChapters()),
                    requestPre.trailingNoiseMarkers() != null
                            ? requestPre.trailingNoiseMarkers() : p.trailingNoiseMarkers(),
                    coalesce(requestPre.chapterPattern(), p.chapterPattern()));
        }
        if (p.chapterPattern() == null || p.chapterPattern().isBlank()) {
            p = new PreprocessOptions(
                    p.normalizeNewlines(), p.stripPageNumbers(), p.mergeCjkHardWrap(),
                    p.collapseBlankLines(), p.trimOutsideChapters(), p.trailingNoiseMarkers(),
                    s.chapterPattern());
        }
        return new SplitPresetCatalog.PresetBundle(s, p);
    }

    /**
     * 按扩展名弱推荐内容类型（可被用户改）。
     */
    public static String suggestByFilename(String filename) {
        if (filename == null) {
            return NARRATIVE;
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return TECH_MARKDOWN;
        }
        if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".py")
                || lower.endsWith(".ts") || lower.endsWith(".js") || lower.endsWith(".go")) {
            return CODE_STRUCTURE;
        }
        return NARRATIVE;
    }

    private static SplitPresetCatalog.PresetBundle bundle(String id, SplitOptions split, PreprocessOptions pre) {
        return new SplitPresetCatalog.PresetBundle(split, pre);
    }

    private static PreprocessOptions clean(boolean trimOutside, String chapterPattern) {
        return new PreprocessOptions(
                true, true, true, true, trimOutside,
                SplitPresetCatalog.DEFAULT_TRAILING_NOISE_MARKERS,
                chapterPattern);
    }

    private static Map<String, Object> meta(String id, String name, String description) {
        SplitPresetCatalog.PresetBundle b = expand(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("contentType", id);
        m.put("strategyId", id);
        m.put("name", name);
        m.put("description", description);
        m.put("split", SplitPresetCatalog.toSplitMap(b.split()));
        m.put("preprocess", SplitPresetCatalog.toPreprocessMap(b.preprocess()));
        return m;
    }

    private static <T> T coalesce(T a, T b) {
        return a != null ? a : b;
    }
}
