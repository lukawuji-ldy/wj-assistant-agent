package com.wuji.assistant.rag.ingest;

import java.util.List;

/**
 * 文本切分器。
 *
 * @author liudy
 */
public interface TextSplitter {

    /**
     * @param text 文本
     * @return 块列表（含 section 提示）
     */
    List<TextChunk> split(String text);

    /**
     * 切分块。
     *
     * @param content 正文
     * @param section 章节
     */
    record TextChunk(String content, String section) {
    }
}
