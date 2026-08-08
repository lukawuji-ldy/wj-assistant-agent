package com.wuji.assistant.server.admin.kb;

/**
 * Chunk 创建/更新请求。
 *
 * @author liudy
 */
public record AdminKbChunkRequest(
        String content,
        String section
) {
}
