package com.wuji.assistant.rag.ingest;

/**
 * 文档预处理器。
 *
 * @author liudy
 */
public interface DocumentPreprocessor {

    /**
     * 使用实例默认选项清洗。
     *
     * @param text 原文
     * @return 清洗后文本
     */
    String preprocess(String text);

    /**
     * 使用局部参数清洗，不改写实例字段（并发安全）。
     *
     * @param text    原文
     * @param options 覆盖项；null 字段回落实例默认
     * @return 清洗后文本
     */
    default String preprocess(String text, PreprocessOptions options) {
        return preprocess(text);
    }

    /**
     * 解析后的有效预处理参数。
     */
    default PreprocessOptions resolve(PreprocessOptions options) {
        return options == null ? PreprocessOptions.defaults() : options;
    }
}
