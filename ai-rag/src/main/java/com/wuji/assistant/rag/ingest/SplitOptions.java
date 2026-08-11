package com.wuji.assistant.rag.ingest;

import java.util.List;

/**
 * 单次切分参数（不改写共享 splitter 实例字段）。
 *
 * @param chunkSize             块大小，null 则用 splitter 默认
 * @param overlap               重叠，null 则用默认
 * @param minChunkLengthToKeep  最短保留长度，null 则用默认
 * @param chapterSplitEnabled   是否章节硬切，null 则用默认
 * @param chapterPattern        章节行首正则，null 则用默认
 * @param sectionTitleMode      section 写入方式，null 则用默认
 * @param separators            递归分隔符优先级，null 则用默认
 * @param keepSeparator         分隔符保留策略，null 则用默认
 * @param preset                选用的预设 id（审计），可空
 * @author liudy
 */
public record SplitOptions(
        Integer chunkSize,
        Integer overlap,
        Integer minChunkLengthToKeep,
        Boolean chapterSplitEnabled,
        String chapterPattern,
        SectionTitleMode sectionTitleMode,
        List<String> separators,
        KeepSeparator keepSeparator,
        String preset
) {
    /**
     * 兼容旧四字段构造。
     */
    public SplitOptions(Integer chunkSize,
                        Integer overlap,
                        Integer minChunkLengthToKeep,
                        Boolean chapterSplitEnabled) {
        this(chunkSize, overlap, minChunkLengthToKeep, chapterSplitEnabled,
                null, null, null, null, null);
    }

    /** 全部回落默认。 */
    public static SplitOptions defaults() {
        return new SplitOptions(null, null, null, null, null, null, null, null, null);
    }
}
