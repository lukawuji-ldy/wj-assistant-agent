package com.wuji.assistant.rag;

import java.util.List;
import java.util.Map;

/**
 * 知识库召回结果。
 *
 * @param hits         命中片段
 * @param rejected     是否拒答
 * @param rejectReason 拒答原因
 * @author liudy
 */
public record RetrievalResult(
        List<Hit> hits,
        boolean rejected,
        String rejectReason
) {
    /**
     * 单条命中。
     *
     * @param chunkId  切片 id
     * @param content  正文
     * @param score    得分（关键词命中为 1.0）
     * @param metadata 元数据
     * @author liudy
     */
    public record Hit(String chunkId, String content, double score, Map<String, Object> metadata) {
    }
}
