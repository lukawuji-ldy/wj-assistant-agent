package com.wuji.assistant.rag.ingest;

/**
 * 单次切分参数（不改写共享 splitter 实例字段）。
 *
 * @param chunkSize             块大小，null 则用 splitter 默认
 * @param overlap               重叠，null 则用默认
 * @param minChunkLengthToKeep  最短保留长度，null 则用默认
 * @param chapterSplitEnabled   是否章节硬切，null 则用默认
 * @author liudy
 */
public record SplitOptions(
        Integer chunkSize,
        Integer overlap,
        Integer minChunkLengthToKeep,
        Boolean chapterSplitEnabled
) {
    /** 全部回落默认。 */
    public static SplitOptions defaults() {
        return new SplitOptions(null, null, null, null);
    }
}
