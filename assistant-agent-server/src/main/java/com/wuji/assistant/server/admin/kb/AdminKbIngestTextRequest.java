package com.wuji.assistant.server.admin.kb;

import java.util.List;

/**
 * 文本入库请求（JSON）。
 *
 * @author liudy
 */
public record AdminKbIngestTextRequest(
        String docId,
        String title,
        String collection,
        String content,
        String source,
        List<String> aclRoles,
        String contentType,
        String preset,
        Integer chunkSize,
        Integer overlap,
        Integer minChunkLengthToKeep,
        Boolean chapterSplitEnabled,
        String chapterPattern,
        String sectionTitleMode,
        List<String> separators,
        String keepSeparator,
        Boolean normalizeNewlines,
        Boolean stripPageNumbers,
        Boolean mergeCjkHardWrap,
        Boolean collapseBlankLines,
        Boolean trimOutsideChapters,
        List<String> trailingNoiseMarkers
) {
    /**
     * 兼容旧四字段切分参数构造（测试 / 旧客户端）。
     */
    public AdminKbIngestTextRequest(
            String docId,
            String title,
            String collection,
            String content,
            String source,
            List<String> aclRoles,
            Integer chunkSize,
            Integer overlap,
            Integer minChunkLengthToKeep,
            Boolean chapterSplitEnabled) {
        this(docId, title, collection, content, source, aclRoles, null, null,
                chunkSize, overlap, minChunkLengthToKeep, chapterSplitEnabled,
                null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * 兼容仅有 preset、无 contentType 的构造。
     */
    public AdminKbIngestTextRequest(
            String docId,
            String title,
            String collection,
            String content,
            String source,
            List<String> aclRoles,
            String preset,
            Integer chunkSize,
            Integer overlap,
            Integer minChunkLengthToKeep,
            Boolean chapterSplitEnabled,
            String chapterPattern,
            String sectionTitleMode,
            List<String> separators,
            String keepSeparator,
            Boolean normalizeNewlines,
            Boolean stripPageNumbers,
            Boolean mergeCjkHardWrap,
            Boolean collapseBlankLines,
            Boolean trimOutsideChapters,
            List<String> trailingNoiseMarkers) {
        this(docId, title, collection, content, source, aclRoles, null, preset,
                chunkSize, overlap, minChunkLengthToKeep, chapterSplitEnabled,
                chapterPattern, sectionTitleMode, separators, keepSeparator,
                normalizeNewlines, stripPageNumbers, mergeCjkHardWrap, collapseBlankLines,
                trimOutsideChapters, trailingNoiseMarkers);
    }
}
