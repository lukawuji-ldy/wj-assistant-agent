package com.wuji.assistant.vta.server.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class VtaAnalyzeDtos {

    public record AnalyzeRequest(
            String transcript,
            String inputType
    ) {
    }

    public record AnalyzeResponse(
            String jobId
    ) {
    }

    public record AnalyzeStreamRequest(
            String jobId,
            String streamId,
            Long lastEventId
    ) {
    }

    public record JobSummary(
            String jobId,
            String status,
            String errorCode,
            String traceId,
            String createdAt
    ) {
    }

    public record JobDetail(
            String jobId,
            String status,
            String errorCode,
            String traceId,
            String inputType,
            String createTime,
            String finishTime,
            JsonNode customerTags,
            JsonNode salesTags,
            JsonNode summary,
            JsonNode intentScore,
            JsonNode aggregate
    ) {
    }

    public record DonePayload(
            String jobId,
            String status,
            JsonNode customerTags,
            JsonNode salesTags,
            JsonNode summary,
            JsonNode intentScore,
            JsonNode aggregate
    ) {
    }
}

