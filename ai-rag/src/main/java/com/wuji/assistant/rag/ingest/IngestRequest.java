package com.wuji.assistant.rag.ingest;

/**
 * 入库请求。
 *
 * @param docId      逻辑文档 id，空则生成
 * @param title      标题
 * @param collection 命名空间
 * @param content    MD/TXT 正文
 * @param source     来源
 * @author liudy
 */
public record IngestRequest(
        String docId,
        String title,
        String collection,
        String content,
        String source
) {
}
