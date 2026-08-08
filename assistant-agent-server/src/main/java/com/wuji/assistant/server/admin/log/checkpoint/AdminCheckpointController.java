package com.wuji.assistant.server.admin.log.checkpoint;

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
 * 管理台 Checkpoint 回放 API（只读）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/logs/checkpoints")
public class AdminCheckpointController {

    private final AdminCheckpointService adminCheckpointService;

    public AdminCheckpointController(AdminCheckpointService adminCheckpointService) {
        this.adminCheckpointService = adminCheckpointService;
    }

    @GetMapping("/threads")
    public Mono<ApiResponse<AdminCheckpointThreadPage>> listThreads(
            @RequestParam(required = false) String threadName,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) Boolean isReleased,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant savedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant savedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminCheckpointService.listThreads(
                                threadName, userId, conversationId, isReleased, savedFrom, savedTo, page, size)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/threads/{threadId}")
    public Mono<ApiResponse<AdminCheckpointThreadDetail>> getThread(@PathVariable String threadId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminCheckpointService.getThread(threadId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/{checkpointId}")
    public Mono<ApiResponse<AdminCheckpointDetail>> getCheckpoint(@PathVariable String checkpointId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminCheckpointService.getCheckpoint(checkpointId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
