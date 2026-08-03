package com.wuji.assistant.rag.ingest;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 中文文档默认预处理。
 *
 * @author liudy
 */
@Component
public class ChineseDocumentPreprocessor implements DocumentPreprocessor {

    @Override
    public String preprocess(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        // 删除独立页码行
        s = s.replaceAll("(?m)^\\s*\\d{1,4}\\s*$", "");
        // 合并 CJK 间硬换行
        s = s.replaceAll("([\\u4e00-\\u9fa5])\\n([\\u4e00-\\u9fa5])", "$1$2");
        // 压缩连续空行
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }
}
