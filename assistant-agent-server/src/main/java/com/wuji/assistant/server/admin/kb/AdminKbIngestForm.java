package com.wuji.assistant.server.admin.kb;

import com.wuji.assistant.rag.ingest.KeepSeparator;
import com.wuji.assistant.rag.ingest.PreprocessOptions;
import com.wuji.assistant.rag.ingest.SectionTitleMode;
import com.wuji.assistant.rag.ingest.SplitOptions;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 管理台上传/预览共用的切分与预处理表单参数。
 * <p>
 * {@code contentType} 为产品主字段；{@code preset} 兼容旧客户端（二者择一，优先 contentType）。
 * </p>
 *
 * @author liudy
 */
public record AdminKbIngestForm(
        String title,
        String collection,
        String docId,
        List<String> aclRoles,
        String contentType,
        String preset,
        Integer chunkSize,
        Integer overlap,
        Integer minChunkLengthToKeep,
        Boolean chapterSplitEnabled,
        String chapterPattern,
        String sectionTitleMode,
        String separatorsJson,
        String keepSeparator,
        Boolean normalizeNewlines,
        Boolean stripPageNumbers,
        Boolean mergeCjkHardWrap,
        Boolean collapseBlankLines,
        Boolean trimOutsideChapters,
        String trailingNoiseMarkersJson
) {
    /**
     * 兼容旧构造（无 contentType）。
     */
    public AdminKbIngestForm(
            String title,
            String collection,
            String docId,
            List<String> aclRoles,
            String preset,
            Integer chunkSize,
            Integer overlap,
            Integer minChunkLengthToKeep,
            Boolean chapterSplitEnabled,
            String chapterPattern,
            String sectionTitleMode,
            String separatorsJson,
            String keepSeparator,
            Boolean normalizeNewlines,
            Boolean stripPageNumbers,
            Boolean mergeCjkHardWrap,
            Boolean collapseBlankLines,
            Boolean trimOutsideChapters,
            String trailingNoiseMarkersJson) {
        this(title, collection, docId, aclRoles, null, preset,
                chunkSize, overlap, minChunkLengthToKeep, chapterSplitEnabled,
                chapterPattern, sectionTitleMode, separatorsJson, keepSeparator,
                normalizeNewlines, stripPageNumbers, mergeCjkHardWrap, collapseBlankLines,
                trimOutsideChapters, trailingNoiseMarkersJson);
    }

    /**
     * 组装请求侧 SplitOptions；strategy id 写入 preset 字段。
     */
    public SplitOptions toSplitOptions() {
        String strategy = blankToNull(contentType);
        if (strategy == null) {
            strategy = blankToNull(preset);
        }
        return new SplitOptions(
                chunkSize,
                overlap,
                minChunkLengthToKeep,
                chapterSplitEnabled,
                blankToNull(chapterPattern),
                SectionTitleMode.parse(sectionTitleMode),
                parseStringList(separatorsJson),
                KeepSeparator.parse(keepSeparator),
                strategy);
    }

    public PreprocessOptions toPreprocessOptions() {
        return new PreprocessOptions(
                normalizeNewlines,
                stripPageNumbers,
                mergeCjkHardWrap,
                collapseBlankLines,
                trimOutsideChapters,
                parseStringList(trailingNoiseMarkersJson),
                blankToNull(chapterPattern));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    static List<String> parseStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("[")) {
            List<String> fromJson = new ArrayList<>();
            String inner = t.substring(1, t.endsWith("]") ? t.length() - 1 : t.length());
            for (String part : inner.split(",")) {
                String p = part.trim().replaceAll("^\"|\"$", "").replace("\\n", "\n");
                if (StringUtils.hasText(p)) {
                    fromJson.add(p);
                }
            }
            return fromJson.isEmpty() ? null : fromJson;
        }
        List<String> out = Arrays.stream(t.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(s -> s.replace("\\n", "\n"))
                .toList();
        return out.isEmpty() ? null : out;
    }
}
