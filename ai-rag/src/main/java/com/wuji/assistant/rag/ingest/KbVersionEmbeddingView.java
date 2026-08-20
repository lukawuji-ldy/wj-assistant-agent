package com.wuji.assistant.rag.ingest;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 文档版本当前 Embedding 指纹视图（管理台）。
 *
 * @param versionId              文档版本
 * @param embeddingConfigId      llm_config.config_id
 * @param embeddingModelVersion  模型指纹
 * @param embeddedChunkCount     已嵌入 ACTIVE chunk 数
 * @param vectorBackend          pgvector | elasticsearch
 * @param indexName              ES index 名；PG 为 null
 * @author liudy
 */
public record KbVersionEmbeddingView(
        @JsonSerialize(using = ToStringSerializer.class) Long versionId,
        String embeddingConfigId,
        String embeddingModelVersion,
        int embeddedChunkCount,
        String vectorBackend,
        String indexName
) {
}
