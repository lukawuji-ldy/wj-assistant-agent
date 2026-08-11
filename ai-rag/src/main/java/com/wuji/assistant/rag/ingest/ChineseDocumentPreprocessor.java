package com.wuji.assistant.rag.ingest;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文文档默认预处理（可按次覆盖）。
 *
 * @author liudy
 */
@Component
public class ChineseDocumentPreprocessor implements DocumentPreprocessor {

    private boolean normalizeNewlines = true;
    private boolean stripPageNumbers = true;
    private boolean mergeCjkHardWrap = true;
    private boolean collapseBlankLines = true;
    private boolean trimOutsideChapters = false;
    private List<String> trailingNoiseMarkers = List.copyOf(SplitPresetCatalog.DEFAULT_TRAILING_NOISE_MARKERS);
    private String chapterPattern = SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN;

    /** 纯装饰分隔行（长度阈值避免误伤 Markdown Setext 的 ===）。 */
    private static final Pattern DECORATIVE_RULE_ONLY =
            Pattern.compile("(?m)^[-=\u2014]{8,}\\s*$");
    /** 标题写在装饰线同一行：保留标题。 */
    private static final Pattern DECORATIVE_RULE_WITH_TITLE =
            Pattern.compile("(?m)^[-=\u2014]{8,}\\s+(.+)$");
    /** 结构标题行：硬换行合并时不得与相邻正文粘连。 */
    private static final Pattern STRUCTURAL_HEADING_LINE = Pattern.compile(
            "^(?:第[一二三四五六七八九十百千零〇两\\d]+[章节篇条款]"
                    + "|[一二三四五六七八九十百千]+、"
                    + "|\\d+(?:\\.\\d+)*\\s+"
                    + "|#{1,6}\\s+"
                    + "|Q\\s*[:：]|问\\s*[:：]|【问】)");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fa5]");

    public void setNormalizeNewlines(boolean normalizeNewlines) {
        this.normalizeNewlines = normalizeNewlines;
    }

    public void setStripPageNumbers(boolean stripPageNumbers) {
        this.stripPageNumbers = stripPageNumbers;
    }

    public void setMergeCjkHardWrap(boolean mergeCjkHardWrap) {
        this.mergeCjkHardWrap = mergeCjkHardWrap;
    }

    public void setCollapseBlankLines(boolean collapseBlankLines) {
        this.collapseBlankLines = collapseBlankLines;
    }

    public void setTrimOutsideChapters(boolean trimOutsideChapters) {
        this.trimOutsideChapters = trimOutsideChapters;
    }

    public void setTrailingNoiseMarkers(List<String> trailingNoiseMarkers) {
        this.trailingNoiseMarkers = trailingNoiseMarkers == null
                ? List.of()
                : List.copyOf(trailingNoiseMarkers);
    }

    public void setChapterPattern(String chapterPattern) {
        if (StringUtils.hasText(chapterPattern)) {
            this.chapterPattern = chapterPattern;
        }
    }

    @Override
    public String preprocess(String text) {
        return preprocess(text, null);
    }

    @Override
    public String preprocess(String text, PreprocessOptions options) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        PreprocessOptions resolved = resolve(options);
        String s = text;
        if (Boolean.TRUE.equals(resolved.normalizeNewlines())) {
            s = s.replace("\r\n", "\n").replace('\r', '\n');
        }
        s = stripDecorativeBanners(s);
        if (Boolean.TRUE.equals(resolved.stripPageNumbers())) {
            s = s.replaceAll("(?m)^\\s*\\d{1,4}\\s*$", "");
        }
        if (Boolean.TRUE.equals(resolved.mergeCjkHardWrap())) {
            s = mergeCjkHardWrap(s);
        }
        if (Boolean.TRUE.equals(resolved.collapseBlankLines())) {
            s = s.replaceAll("\\n{3,}", "\n\n");
        }
        s = s.trim();
        if (Boolean.TRUE.equals(resolved.trimOutsideChapters())) {
            s = trimOutside(s, resolved.chapterPattern());
        }
        s = stripTrailingNoise(s, resolved.trailingNoiseMarkers());
        return s.trim();
    }

    @Override
    public PreprocessOptions resolve(PreprocessOptions options) {
        return new PreprocessOptions(
                options != null && options.normalizeNewlines() != null
                        ? options.normalizeNewlines() : normalizeNewlines,
                options != null && options.stripPageNumbers() != null
                        ? options.stripPageNumbers() : stripPageNumbers,
                options != null && options.mergeCjkHardWrap() != null
                        ? options.mergeCjkHardWrap() : mergeCjkHardWrap,
                options != null && options.collapseBlankLines() != null
                        ? options.collapseBlankLines() : collapseBlankLines,
                options != null && options.trimOutsideChapters() != null
                        ? options.trimOutsideChapters() : trimOutsideChapters,
                options != null && options.trailingNoiseMarkers() != null
                        ? List.copyOf(options.trailingNoiseMarkers())
                        : List.copyOf(trailingNoiseMarkers),
                options != null && StringUtils.hasText(options.chapterPattern())
                        ? options.chapterPattern() : chapterPattern);
    }

    private static String mergeCjkHardWrap(String text) {
        String[] lines = text.split("\n", -1);
        if (lines.length <= 1) {
            return text;
        }
        StringBuilder out = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String prev = lastLine(out);
            String curr = lines[i];
            if (shouldMergeCjkWrap(prev, curr)) {
                out.append(curr);
            } else {
                out.append('\n').append(curr);
            }
        }
        return out.toString();
    }

    private static boolean shouldMergeCjkWrap(String prev, String curr) {
        if (!StringUtils.hasText(prev) || !StringUtils.hasText(curr)) {
            return false;
        }
        if (isStructuralHeadingLine(prev) || isStructuralHeadingLine(curr)) {
            return false;
        }
        return endsWithCjk(prev) && startsWithCjk(curr);
    }

    private static boolean isStructuralHeadingLine(String line) {
        return STRUCTURAL_HEADING_LINE.matcher(line.trim()).find();
    }

    private static boolean endsWithCjk(String s) {
        return CJK.matcher(s.substring(s.length() - 1)).matches();
    }

    private static boolean startsWithCjk(String s) {
        String t = s.stripLeading();
        return !t.isEmpty() && CJK.matcher(t.substring(0, 1)).matches();
    }

    private static String lastLine(StringBuilder buf) {
        int nl = buf.lastIndexOf("\n");
        return nl < 0 ? buf.toString() : buf.substring(nl + 1);
    }

    private static String stripDecorativeBanners(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String unwrapped = DECORATIVE_RULE_WITH_TITLE.matcher(text).replaceAll("$1");
        return DECORATIVE_RULE_ONLY.matcher(unwrapped).replaceAll("");
    }

    private static String trimOutside(String text, String patternStr) {
        if (!StringUtils.hasText(patternStr) || !StringUtils.hasText(text)) {
            return text;
        }
        Pattern pattern = Pattern.compile(patternStr);
        Matcher m = pattern.matcher(text);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        if (starts.isEmpty()) {
            return text;
        }
        int from = starts.get(0);
        int to = text.length();
        // 末章结束：若有 trailing 已在后续处理；此处保留至文末，由 noise markers 再裁
        return text.substring(from, to).trim();
    }

    private static String stripTrailingNoise(String text, List<String> markers) {
        if (!StringUtils.hasText(text) || markers == null || markers.isEmpty()) {
            return text;
        }
        int cut = -1;
        for (String marker : markers) {
            if (!StringUtils.hasText(marker)) {
                continue;
            }
            int idx = text.indexOf(marker);
            if (idx >= 0 && (cut < 0 || idx < cut)) {
                cut = idx;
            }
        }
        if (cut > 0) {
            return text.substring(0, cut).trim();
        }
        return text;
    }
}
