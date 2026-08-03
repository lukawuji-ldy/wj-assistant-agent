package com.wuji.assistant.rag.ingest;

/**
 * 文档预处理器。
 *
 * @author liudy
 */
public interface DocumentPreprocessor {

    /**
     * @param text 原文
     * @return 清洗后文本
     */
    String preprocess(String text);
}
