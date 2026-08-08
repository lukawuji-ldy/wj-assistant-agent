package com.wuji.assistant.server.admin.log.llm;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.server.admin.security.CurrentAdmin;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

/**
 * 管理台 LLM 调用日志 API（只读）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/logs/llm-calls")
public class AdminLlmCallLogController {

    private final AdminLlmCallLogService adminLlmCallLogService;

    public AdminLlmCallLogController(AdminLlmCallLogService adminLlmCallLogService) {
        this.adminLlmCallLogService = adminLlmCallLogService;
    }

    @GetMapping
    public Mono<ApiResponse<AdminLlmCallLogPage>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String callId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isFallback,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createTimeFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createTimeTo,
            @RequestParam(required = false) Integer latencyMsMin,
            @RequestParam(required = false) Integer latencyMsMax,
            @RequestParam(required = false) Integer promptTokensMin,
            @RequestParam(required = false) Integer promptTokensMax,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminLlmCallLogService.list(
                                userId, conversationId, messageId, callId, traceId, modelId, provider, status,
                                isFallback, createTimeFrom, createTimeTo,
                                latencyMsMin, latencyMsMax, promptTokensMin, promptTokensMax, page, size)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/{callId}")
    public Mono<ApiResponse<AdminLlmCallLogDetail>> get(@PathVariable String callId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminLlmCallLogService.get(callId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
