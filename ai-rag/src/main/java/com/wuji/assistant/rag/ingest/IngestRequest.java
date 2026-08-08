package com.wuji.assistant.rag.ingest;

import java.util.List;

/**
 * 入库请求。
 *
 * @param docId        逻辑文档 id，空则生成
 * @param title        标题
 * @param collection   命名空间
 * @param content      正文（已解析的纯文本）
 * @param source       来源（可写文件名）
 * @param aclRoles     可见角色；null/空 → []
 * @param splitOptions 切分参数；null → splitter 默认
 * @param sourceFile   源文件名（写入 ingest_options）
 * @param parser       解析器标识：plaintext | pdfbox
 * @author liudy
 */
public record IngestRequest(
        String docId,
        String title,
        String collection,
        String content,
        String source,
        List<String> aclRoles,
        SplitOptions splitOptions,
        String sourceFile,
        String parser
) {
    /**
     * 兼容聊天侧旧调用（无切分/ACL 覆盖）。
     */
    public IngestRequest(String docId, String title, String collection, String content, String source) {
        this(docId, title, collection, content, source, null, null, null, null);
    }
}
