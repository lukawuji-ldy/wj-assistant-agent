package com.wuji.assistant.server.admin.kb;

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
        java.util.List<String> aclRoles,
        Integer chunkSize,
        Integer overlap,
        Integer minChunkLengthToKeep,
        Boolean chapterSplitEnabled
) {
}
