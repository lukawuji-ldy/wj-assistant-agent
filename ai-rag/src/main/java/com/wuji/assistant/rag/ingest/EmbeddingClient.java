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

    /**
     * @return llm_config.config_id（EMBEDDING）；不可用时返回占位
     */
    default String embeddingConfigId() {
        return "unavailable";
    }

    /**
     * 指纹：{@code configId|model|dimensions}。
     */
    default String embeddingModelVersion() {
        return "unavailable|unknown|1536";
    }

    /**
     * 向量维度（来自 llm_config EMBEDDING extra_json.dimensions）。
     */
    default int dimensions() {
        String fp = embeddingModelVersion();
        if (fp == null || fp.isBlank()) {
            return 1536;
        }
        int last = fp.lastIndexOf('|');
        if (last < 0 || last >= fp.length() - 1) {
            return 1536;
        }
        try {
            return Integer.parseInt(fp.substring(last + 1));
        } catch (NumberFormatException e) {
            return 1536;
        }
    }
}
