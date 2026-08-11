package com.wuji.assistant.rag.ingest;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文递归切分：章节硬切 → 段落 → 句读。
 * 按次覆盖请用 {@link #split(String, SplitOptions)}，勿并发改写实例字段。
 *
 * @author liudy
 */
@Component
public class ChineseRecursiveTextSplitter implements TextSplitter {

    private int chunkSize = 500;
    private int overlap = 80;
    private int minChunkLengthToKeep = 50;
    private boolean chapterSplitEnabled = true;
    private String chapterPattern = SplitPresetCatalog.DEFAULT_CHAPTER_PATTERN;
    private SectionTitleMode sectionTitleMode = SectionTitleMode.FULL_LINE;
    private List<String> separators = List.copyOf(SplitPresetCatalog.DEFAULT_SEPARATORS);
    private KeepSeparator keepSeparator = KeepSeparator.APPEND;

    private static final Pattern CHAPTER_HEADING =
            Pattern.compile("^第[一二三四五六七八九十百千零〇两\\d]+章");
    private static final Pattern ARTICLE_HEADING =
            Pattern.compile("^第[一二三四五六七八九十百千零〇两\\d]+条");
    private static final Pattern FAQ_HEADING =
            Pattern.compile("^(?:Q\\s*[:：]|问\\s*[:：]|【问】)");
    private static final Pattern DECORATIVE_PREFIX =
            Pattern.compile("^[-=\u2014]{3,}\\s*");

    public void setChunkSize(int chunkSize) {
        this.chunkSize = Math.max(50, chunkSize);
    }

    public void setOverlap(int overlap) {
        this.overlap = Math.max(0, overlap);
    }

    public void setMinChunkLengthToKeep(int minChunkLengthToKeep) {
        this.minChunkLengthToKeep = Math.max(1, minChunkLengthToKeep);
    }

    public void setChapterSplitEnabled(boolean chapterSplitEnabled) {
        this.chapterSplitEnabled = chapterSplitEnabled;
    }

    public void setChapterPattern(String chapterPattern) {
        if (StringUtils.hasText(chapterPattern)) {
            this.chapterPattern = chapterPattern;
        }
    }

    public void setSectionTitleMode(SectionTitleMode sectionTitleMode) {
        if (sectionTitleMode != null) {
            this.sectionTitleMode = sectionTitleMode;
        }
    }

    public void setSeparators(List<String> separators) {
        if (separators != null && !separators.isEmpty()) {
            this.separators = List.copyOf(separators);
        }
    }

    public void setKeepSeparator(KeepSeparator keepSeparator) {
        if (keepSeparator != null) {
            this.keepSeparator = keepSeparator;
        }
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }

    public int getMinChunkLengthToKeep() {
        return minChunkLengthToKeep;
    }

    public boolean isChapterSplitEnabled() {
        return chapterSplitEnabled;
    }

    @Override
    public List<TextChunk> split(String text) {
        return split(text, null);
    }

    /**
     * 使用局部参数切分，不改写实例字段（并发安全）。
     */
    public List<TextChunk> split(String text, SplitOptions options) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        SplitOptions resolved = resolve(options);
        validateOverlap(resolved);
        int size = resolved.chunkSize();
        int ov = resolved.overlap();
        int minKeep = resolved.minChunkLengthToKeep();
        boolean chapter = Boolean.TRUE.equals(resolved.chapterSplitEnabled());
        Pattern chapterPat = Pattern.compile(resolved.chapterPattern());
        SectionTitleMode titleMode = resolved.sectionTitleMode() == null
                ? SectionTitleMode.FULL_LINE : resolved.sectionTitleMode();
        List<String> seps = resolved.separators() == null || resolved.separators().isEmpty()
                ? SplitPresetCatalog.DEFAULT_SEPARATORS : resolved.separators();
        KeepSeparator keep = resolved.keepSeparator() == null ? KeepSeparator.APPEND : resolved.keepSeparator();

        List<Section> sections = chapter
                ? applyChapterArticleHierarchy(splitByChapter(text, chapterPat, titleMode))
                : List.of(new Section("", text));
        List<TextChunk> out = new ArrayList<>();
        for (Section section : sections) {
            out.addAll(splitRecursive(section.title(), section.body(), size, ov, minKeep, seps, keep));
        }
        return mergeShort(out, minKeep);
    }

    /**
     * 解析后的有效切分参数（写入 ingest_options）。
     */
    public SplitOptions resolve(SplitOptions options) {
        return new SplitOptions(
                resolveChunkSize(options),
                resolveOverlap(options),
                resolveMinKeep(options),
                resolveChapter(options),
                resolveChapterPattern(options),
                resolveSectionTitleMode(options),
                resolveSeparators(options),
                resolveKeepSeparator(options),
                options != null ? options.preset() : null);
    }

    static void validateOverlap(SplitOptions resolved) {
        if (resolved.overlap() != null && resolved.chunkSize() != null
                && resolved.overlap() >= resolved.chunkSize()) {
            throw new IllegalArgumentException("overlap 必须小于 chunkSize");
        }
    }

    private int resolveChunkSize(SplitOptions options) {
        if (options != null && options.chunkSize() != null) {
            return Math.max(50, options.chunkSize());
        }
        return chunkSize;
    }

    private int resolveOverlap(SplitOptions options) {
        if (options != null && options.overlap() != null) {
            return Math.max(0, options.overlap());
        }
        return overlap;
    }

    private int resolveMinKeep(SplitOptions options) {
        if (options != null && options.minChunkLengthToKeep() != null) {
            return Math.max(1, options.minChunkLengthToKeep());
        }
        return minChunkLengthToKeep;
    }

    private boolean resolveChapter(SplitOptions options) {
        if (options != null && options.chapterSplitEnabled() != null) {
            return options.chapterSplitEnabled();
        }
        return chapterSplitEnabled;
    }

    private String resolveChapterPattern(SplitOptions options) {
        if (options != null && StringUtils.hasText(options.chapterPattern())) {
            return options.chapterPattern();
        }
        return chapterPattern;
    }

    private SectionTitleMode resolveSectionTitleMode(SplitOptions options) {
        if (options != null && options.sectionTitleMode() != null) {
            return options.sectionTitleMode();
        }
        return sectionTitleMode;
    }

    private List<String> resolveSeparators(SplitOptions options) {
        if (options != null && options.separators() != null && !options.separators().isEmpty()) {
            return List.copyOf(options.separators());
        }
        return List.copyOf(separators);
    }

    private KeepSeparator resolveKeepSeparator(SplitOptions options) {
        if (options != null && options.keepSeparator() != null) {
            return options.keepSeparator();
        }
        return keepSeparator;
    }

    private List<Section> splitByChapter(String text, Pattern chapterPat, SectionTitleMode titleMode) {
        Matcher m = chapterPat.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            titles.add(resolveTitle(text, m, titleMode));
        }
        if (starts.isEmpty()) {
            return List.of(new Section("", text));
        }
        List<Section> sections = new ArrayList<>();
        if (starts.get(0) > 0) {
            sections.add(new Section("", text.substring(0, starts.get(0)).trim()));
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            String body = text.substring(from, to).trim();
            sections.add(new Section(titles.get(i), body));
        }
        return sections;
    }

    /**
     * 章/条两级：空章不产出切片，条的 section 带上所属章；FAQ 前的附录标题并入问答块。
     */
    private static List<Section> applyChapterArticleHierarchy(List<Section> sections) {
        List<Section> hierarchical = new ArrayList<>();
        String currentChapter = "";
        for (Section section : sections) {
            HeadingKind kind = classifyHeading(section.title());
            if (kind == HeadingKind.CHAPTER) {
                currentChapter = normalizeHeading(section.title());
                if (!StringUtils.hasText(bodyAfterTitle(section))) {
                    continue;
                }
                hierarchical.add(new Section(currentChapter, section.body()));
                continue;
            }
            if (kind == HeadingKind.ARTICLE) {
                String articleTitle = normalizeHeading(section.title());
                String path = StringUtils.hasText(currentChapter)
                        ? currentChapter + " / " + articleTitle
                        : articleTitle;
                hierarchical.add(new Section(path, section.body()));
                continue;
            }
            hierarchical.add(section);
        }
        return peelTrailingHeadingsIntoFaq(hierarchical);
    }

    private static List<Section> peelTrailingHeadingsIntoFaq(List<Section> sections) {
        if (sections.size() < 2) {
            return sections;
        }
        List<Section> out = new ArrayList<>(sections);
        for (int i = 0; i < out.size() - 1; i++) {
            if (classifyHeading(out.get(i + 1).title()) != HeadingKind.FAQ) {
                continue;
            }
            String[] peeled = peelTrailingHeading(out.get(i).body());
            if (!StringUtils.hasText(peeled[1])) {
                continue;
            }
            out.set(i, new Section(out.get(i).title(), peeled[0]));
            out.set(i + 1, new Section(out.get(i + 1).title(),
                    (peeled[1] + "\n" + out.get(i + 1).body()).trim()));
        }
        return out;
    }

    private static String[] peelTrailingHeading(String body) {
        if (!StringUtils.hasText(body)) {
            return new String[] {body == null ? "" : body, ""};
        }
        String[] lines = body.split("\n", -1);
        int end = lines.length;
        while (end > 0 && !StringUtils.hasText(lines[end - 1])) {
            end--;
        }
        int peelFrom = end;
        while (peelFrom > 1) {
            String line = lines[peelFrom - 1].trim();
            if (line.isEmpty() || isAppendixHeading(line)) {
                peelFrom--;
                continue;
            }
            break;
        }
        if (peelFrom >= end) {
            return new String[] {body, ""};
        }
        String kept = String.join("\n", Arrays.copyOfRange(lines, 0, peelFrom)).trim();
        String peeled = String.join("\n", Arrays.copyOfRange(lines, peelFrom, lines.length)).trim();
        return new String[] {kept, peeled};
    }

    private static boolean isAppendixHeading(String line) {
        String normalized = normalizeHeading(line);
        if (CHAPTER_HEADING.matcher(normalized).find() || ARTICLE_HEADING.matcher(normalized).find()) {
            return false;
        }
        if (normalized.indexOf('。') >= 0 || normalized.indexOf('！') >= 0 || normalized.indexOf('？') >= 0) {
            return false;
        }
        return normalized.length() <= 80;
    }

    private static String bodyAfterTitle(Section section) {
        String body = section.body() == null ? "" : section.body();
        String title = section.title() == null ? "" : section.title();
        if (!StringUtils.hasText(title)) {
            return body.trim();
        }
        if (body.startsWith(title)) {
            return body.substring(title.length()).trim();
        }
        int nl = body.indexOf('\n');
        String first = (nl < 0 ? body : body.substring(0, nl)).trim();
        if (first.equals(title.trim()) || first.equals(normalizeHeading(title))) {
            return (nl < 0 ? "" : body.substring(nl + 1)).trim();
        }
        return body.trim();
    }

    private static HeadingKind classifyHeading(String title) {
        String normalized = normalizeHeading(title);
        if (!StringUtils.hasText(normalized)) {
            return HeadingKind.OTHER;
        }
        if (CHAPTER_HEADING.matcher(normalized).find()) {
            return HeadingKind.CHAPTER;
        }
        if (ARTICLE_HEADING.matcher(normalized).find()) {
            return HeadingKind.ARTICLE;
        }
        if (FAQ_HEADING.matcher(normalized).find()) {
            return HeadingKind.FAQ;
        }
        return HeadingKind.OTHER;
    }

    private static String normalizeHeading(String title) {
        if (title == null) {
            return "";
        }
        return DECORATIVE_PREFIX.matcher(title.trim()).replaceFirst("").trim();
    }

    private enum HeadingKind {
        CHAPTER, ARTICLE, FAQ, OTHER
    }

    private static String resolveTitle(String text, Matcher m, SectionTitleMode titleMode) {
        if (titleMode == SectionTitleMode.MATCH) {
            return m.group().trim();
        }
        int lineEnd = text.indexOf('\n', m.start());
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        return text.substring(m.start(), lineEnd).trim();
    }

    private List<TextChunk> splitRecursive(String section, String body, int size, int ov, int minKeep,
                                           List<String> seps, KeepSeparator keep) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        if (body.length() <= size) {
            return List.of(new TextChunk(body, section));
        }
        List<String> parts = splitBySeparators(body, seps, keep);
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String part : parts) {
            if (buf.length() + part.length() > size && buf.length() >= minKeep) {
                chunks.add(new TextChunk(buf.toString().trim(), section));
                String prev = buf.toString();
                buf.setLength(0);
                if (ov > 0 && prev.length() > ov) {
                    buf.append(prev.substring(prev.length() - ov));
                }
            }
            buf.append(part);
        }
        if (buf.length() > 0) {
            chunks.add(new TextChunk(buf.toString().trim(), section));
        }
        return chunks;
    }

    private static List<String> splitBySeparators(String text, List<String> seps, KeepSeparator keep) {
        List<String> current = List.of(text);
        for (String sep : seps) {
            List<String> next = new ArrayList<>();
            for (String piece : current) {
                if (piece.length() <= 1) {
                    next.add(piece);
                    continue;
                }
                String[] arr = piece.split(Pattern.quote(sep), -1);
                for (int i = 0; i < arr.length; i++) {
                    String s = arr[i];
                    if (i > 0 && keep == KeepSeparator.PREPEND) {
                        s = sep + s;
                    } else if (i < arr.length - 1 && keep == KeepSeparator.APPEND) {
                        s = s + sep;
                    }
                    if (!s.isEmpty()) {
                        next.add(s);
                    }
                }
            }
            current = next;
        }
        return current;
    }

    private List<TextChunk> mergeShort(List<TextChunk> chunks, int minKeep) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        List<TextChunk> merged = new ArrayList<>();
        TextChunk pending = null;
        for (TextChunk c : chunks) {
            if (pending == null) {
                pending = c;
                continue;
            }
            if (pending.content().length() < minKeep
                    && pending.section().equals(c.section())) {
                pending = new TextChunk(pending.content() + c.content(), pending.section());
            } else {
                merged.add(pending);
                pending = c;
            }
        }
        if (pending != null) {
            merged.add(pending);
        }
        return merged;
    }

    private record Section(String title, String body) {
    }
}
