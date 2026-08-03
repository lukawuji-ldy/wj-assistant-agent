package com.wuji.assistant.rag.ingest;

/**
 * Embedding 客户端（由 agent-server / agent-core 提供实现）。
 *
 * @author liudy
 */
public interface EmbeddingClient {

    /**
     * @return 是否可用（有 Key / 模型）
     */
    boolean available();

    /**
     * @param text 文本
     * @return 向量；不可用返回 null
     */
    float[] embed(String text);
}
