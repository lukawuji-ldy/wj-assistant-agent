package com.wuji.assistant.rag.ingest;

import java.util.List;
import java.util.Map;

/**
 * 切分预览结果（不落库）。
 *
 * @author liudy
 */
public record SplitPreviewResult(
        int chunkCount,
        boolean truncated,
        int cleanedLength,
        Map<String, Object> resolvedOptions,
        List<PreviewChunk> chunks,
        List<String> warnings
) {
    /**
     * 预览中的单个片段。
     */
    public record PreviewChunk(int seq, String section, int length, String content) {
    }
}
