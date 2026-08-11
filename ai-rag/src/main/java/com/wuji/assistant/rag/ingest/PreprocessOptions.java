package com.wuji.assistant.rag.ingest;

import java.util.List;

/**
 * 单次预处理参数（不改写共享 preprocessor 实例字段）。
 *
 * @param normalizeNewlines     统一换行；null → 默认
 * @param stripPageNumbers      删除独立页码行；null → 默认
 * @param mergeCjkHardWrap      合并 CJK 间硬换行；null → 默认
 * @param collapseBlankLines    压缩连续空行；null → 默认
 * @param trimOutsideChapters   裁切首章前/末章后杂讯；null → 默认
 * @param trailingNoiseMarkers  文末杂讯起点标记；null → 默认列表
 * @param chapterPattern        裁切用章节正则；null → 与切分默认一致
 * @author liudy
 */
public record PreprocessOptions(
        Boolean normalizeNewlines,
        Boolean stripPageNumbers,
        Boolean mergeCjkHardWrap,
        Boolean collapseBlankLines,
        Boolean trimOutsideChapters,
        List<String> trailingNoiseMarkers,
        String chapterPattern
) {
    /** 全部回落默认。 */
    public static PreprocessOptions defaults() {
        return new PreprocessOptions(null, null, null, null, null, null, null);
    }
}
