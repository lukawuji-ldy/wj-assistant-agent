package com.wuji.assistant.rag.ingest;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文递归切分：章节硬切 → 段落 → 句读 → 字符。
 * 按次覆盖请用 {@link #split(String, SplitOptions)}，勿并发改写实例字段。
 *
 * @author liudy
 */
@Component
public class ChineseRecursiveTextSplitter implements TextSplitter {

    private static final Pattern CHAPTER = Pattern.compile(
            "(?m)^([一二三四五六七八九十百千]+、|第[一二三四五六七八九十百千\\d]+[章节篇]|\\d+(\\.\\d+)*\\s+)");

    private int chunkSize = 500;
    private int overlap = 80;
    private int minChunkLengthToKeep = 50;
    private boolean chapterSplitEnabled = true;

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
     *
     * @param text    文本
     * @param options 覆盖项；null 字段回落实例默认
     * @return 块列表
     */
    public List<TextChunk> split(String text, SplitOptions options) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        int size = resolveChunkSize(options);
        int ov = resolveOverlap(options);
        int minKeep = resolveMinKeep(options);
        boolean chapter = resolveChapter(options);
        List<Section> sections = chapter ? splitByChapter(text) : List.of(new Section("", text));
        List<TextChunk> out = new ArrayList<>();
        for (Section section : sections) {
            out.addAll(splitRecursive(section.title(), section.body(), size, ov, minKeep));
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
                resolveChapter(options));
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

    private List<Section> splitByChapter(String text) {
        Matcher m = CHAPTER.matcher(text);
        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            titles.add(m.group().trim());
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

    private List<TextChunk> splitRecursive(String section, String body, int size, int ov, int minKeep) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        if (body.length() <= size) {
            return List.of(new TextChunk(body, section));
        }
        List<String> parts = splitBySeparators(body, List.of("\n\n", "\n", "。", "！", "？", "；", "，"));
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

    private static List<String> splitBySeparators(String text, List<String> separators) {
        List<String> current = List.of(text);
        for (String sep : separators) {
            List<String> next = new ArrayList<>();
            for (String piece : current) {
                if (piece.length() <= 1) {
                    next.add(piece);
                    continue;
                }
                String[] arr = piece.split(Pattern.quote(sep), -1);
                for (int i = 0; i < arr.length; i++) {
                    String s = arr[i];
                    if (i < arr.length - 1) {
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
