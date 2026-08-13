package com.wuji.assistant.vta;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 录音分析助手（VTA）最终结果（页面展示 + raw）。
 */
public record VtaAnalysisResult(
        String jobId,
        VtaAnalysisStatus status,
        String aggregateText,
        JsonNode customerTags,
        JsonNode salesTags,
        JsonNode summary,
        JsonNode intentScore,
        JsonNode aggregateRaw,
        JsonNode rawNodeOutputs
) {
}

