package com.wuji.assistant.vta.server.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.vta.VtaAnalysisExecutor;
import com.wuji.assistant.vta.VtaAnalysisObserver;
import com.wuji.assistant.vta.VtaAnalysisStatus;
import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.vta.server.repo.AnalysisJobRepository;
import com.wuji.assistant.vta.server.sse.VtaStreamSessionRegistry;
import com.wuji.assistant.vta.server.web.dto.VtaAnalyzeDtos.AnalyzeRequest;
import com.wuji.assistant.vta.server.web.dto.VtaAnalyzeDtos.AnalyzeResponse;
import com.wuji.assistant.vta.server.web.dto.VtaAnalyzeDtos.AnalyzeStreamRequest;
import com.wuji.assistant.vta.server.web.dto.VtaAnalyzeDtos.DonePayload;
import com.wuji.assistant.vta.server.web.dto.VtaAnalyzeDtos.JobDetail;
import com.wuji.assistant.vta.server.web.dto.VtaAnalyzeDtos.JobSummary;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/vta")
public class VtaController {

    private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    private final AnalysisJobRepository jobRepository;
    private final VtaAnalysisExecutor vtaAnalysisExecutor;
    private final ObjectMapper objectMapper;
    private final VtaStreamSessionRegistry streamRegistry;

    private final int maxTranscriptChars;

    public VtaController(AnalysisJobRepository jobRepository,
                          VtaAnalysisExecutor vtaAnalysisExecutor,
                          ObjectMapper objectMapper,
                          VtaStreamSessionRegistry streamRegistry,
                          @Value("${wuji.vta.input.max-chars:20000}") int maxTranscriptChars) {
        this.jobRepository = jobRepository;
        this.vtaAnalysisExecutor = vtaAnalysisExecutor;
        this.objectMapper = objectMapper;
        this.streamRegistry = streamRegistry;
        this.maxTranscriptChars = maxTranscriptChars;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<AnalyzeResponse>> analyze(@RequestBody AnalyzeRequest request) {
        if (request == null || !StringUtils.hasText(request.transcript())) {
            return Mono.error(new WujiException(ErrorCode.BAD_REQUEST, "VTA_TRANSCRIPT_EMPTY"));
        }
        String rawTranscript = request.transcript().trim();
        String transcript = rawTranscript.length() > maxTranscriptChars
                ? rawTranscript.substring(0, maxTranscriptChars)
                : rawTranscript;

        return com.wuji.assistant.vta.server.security.CurrentUser.require()
                .flatMap(user -> Mono.fromCallable(() -> jobRepository.createPending(
                                "VTA",
                                user.userId(),
                                user.tenantId(),
                                request.inputType() == null ? "TEXT" : request.inputType(),
                                transcript))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(jobId -> ApiResponse.ok(new AnalyzeResponse(jobId))));
    }

    @PostMapping(value = "/analyze/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestBody(required = false) AnalyzeStreamRequest request,
            @RequestHeader(value = LAST_EVENT_ID_HEADER, required = false) String lastEventId) {

        AnalyzeStreamRequest effective = mergeLastEventId(request, lastEventId);
        if (effective == null || !StringUtils.hasText(effective.jobId())) {
            return Flux.error(new WujiException(ErrorCode.BAD_REQUEST, "VTA_JOB_ID_REQUIRED"));
        }

        String jobId = effective.jobId().trim();

        return com.wuji.assistant.vta.server.security.CurrentUser.require()
                .flatMapMany(user -> Mono.fromCallable(() -> jobRepository.findOwned(jobId, user.userId())
                                .orElseThrow(() -> new WujiException(ErrorCode.NOT_FOUND, "VTA_JOB_NOT_FOUND")))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(row -> {
                            VtaStreamSessionRegistry.Entry entry = streamRegistry.getOrCreate(jobId, user.userId());
                            Long last = effective.lastEventId();
                            if (entry.markStarted()) {
                                startAnalysis(user.userId(), row, entry);
                            }
                            return streamRegistry.stream(entry, last);
                        }));
    }

    @GetMapping(value = "/jobs/{jobId}")
    public Mono<ApiResponse<JobDetail>> getJob(@PathVariable String jobId) {
        return com.wuji.assistant.vta.server.security.CurrentUser.require()
                .flatMap(user -> Mono.fromCallable(() -> jobRepository.findOwnedDetail(jobId, user.userId()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(opt -> opt.map(detail -> {
                                    var job = detail.job();
                                    return Mono.just(ApiResponse.ok(new JobDetail(
                                            job.jobId(),
                                            job.status(),
                                            job.errorCode(),
                                            job.traceId(),
                                            job.inputType(),
                                            job.createTime() == null ? null : job.createTime().toString(),
                                            job.finishTime() == null ? null : job.finishTime().toString(),
                                            detail.customerTags(),
                                            detail.salesTags(),
                                            detail.summary(),
                                            detail.intent(),
                                            detail.aggregate())));
                                })
                                .orElseGet(() -> Mono.error(new WujiException(ErrorCode.NOT_FOUND, "VTA_JOB_NOT_FOUND")))));
    }

    @GetMapping(value = "/jobs")
    public Mono<ApiResponse<java.util.List<JobSummary>>> listJobs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return com.wuji.assistant.vta.server.security.CurrentUser.require()
                .flatMap(user -> Mono.fromCallable(() -> jobRepository.listOwned(user.userId(), page, size, status).stream()
                                .map(row -> new JobSummary(
                                        row.jobId(),
                                        row.status(),
                                        row.errorCode(),
                                        row.traceId(),
                                        row.createTime() == null ? null : row.createTime().toString()))
                                .toList())
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(ApiResponse::ok));
    }

    private void startAnalysis(String userId, AnalysisJobRepository.AnalysisJobRow row, VtaStreamSessionRegistry.Entry entry) {
        String traceId = IdGenerator.nextBizId("tr_");
        jobRepository.markRunning(row.jobId(), traceId);

        // meta 事件让前端知道 streamId
        try {
            String meta = objectMapper.writeValueAsString(
                    java.util.Map.of("streamId", entry.session().getStreamId()));
            ServerSentEvent<String> metaEvent = entry.session().append("meta", meta);
            entry.emitNext(metaEvent);
        } catch (JsonProcessingException ignore) {
            // ignore
        }

        VtaAnalysisObserver observer = (nodeName, parsedJson) -> {
            try {
                String data = parsedJson == null ? "null" : objectMapper.writeValueAsString(parsedJson);
                ServerSentEvent<String> ev = entry.session().append(nodeName, data);
                entry.emitNext(ev);
            } catch (Exception ignore) {
                // SSE 发送失败不影响落库
            }
        };

        // 后台执行并落库
        Mono.fromCallable(() -> vtaAnalysisExecutor.execute(
                        row.jobId(),
                        userId,
                        row.transcriptText(),
                        traceId,
                        observer))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(result -> {
                    VtaAnalysisStatus status = result.status();
                    // PARTIAL 视作已生成结果（但字段可能带 error）
                    jobRepository.upsertResult(
                            row.jobId(),
                            result.customerTags(),
                            result.salesTags(),
                            result.summary(),
                            result.intentScore(),
                            objectMapper.valueToTree(java.util.Map.of(
                                    "aggregateText", result.aggregateText(),
                                    "raw", result.aggregateRaw())),
                            result.rawNodeOutputs());
                    switch (status) {
                        case SUCCEEDED -> jobRepository.markSucceeded(row.jobId());
                        case PARTIAL -> jobRepository.markPartial(row.jobId(), "PARTIAL");
                        default -> jobRepository.markFailed(row.jobId(), "FAILED");
                    }

                    // done 事件（前端用来渲染最终 aggregate）
                    try {
                        var done = new DonePayload(
                                row.jobId(),
                                status.name(),
                                result.customerTags(),
                                result.salesTags(),
                                result.summary(),
                                result.intentScore(),
                                objectMapper.valueToTree(java.util.Map.of(
                                        "aggregateText", result.aggregateText(),
                                        "raw", result.aggregateRaw())));
                        String doneJson = objectMapper.writeValueAsString(done);
                        ServerSentEvent<String> ev = entry.session().append("done", doneJson);
                        entry.emitNext(ev);
                    } catch (Exception ignore) {
                        // ignore
                    }

                    entry.markCompleted();
                    entry.sink().tryEmitComplete();
                }, ex -> {
                    jobRepository.markFailed(row.jobId(), "EXEC_FAILED");
                    try {
                        String data = "{\"code\":\"VTA_EXEC_FAILED\",\"message\":\"" + ex.getMessage() + "\"}";
                        ServerSentEvent<String> ev = entry.session().append("error", data);
                        entry.emitNext(ev);
                    } catch (Exception ignore) {
                        // ignore
                    }
                    entry.markCompleted();
                    entry.sink().tryEmitComplete();
                });
    }

    private static AnalyzeStreamRequest mergeLastEventId(AnalyzeStreamRequest request, String lastEventIdHeader) {
        Long headerId = parseLong(lastEventIdHeader);
        if (request == null) {
            return new AnalyzeStreamRequest(null, null, headerId);
        }
        if (request.lastEventId() != null || headerId == null) {
            return request;
        }
        return new AnalyzeStreamRequest(
                request.jobId(),
                request.streamId(),
                headerId
        );
    }

    private static Long parseLong(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

