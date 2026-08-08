package com.wuji.assistant.server.admin.memory;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.server.admin.security.CurrentAdmin;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

/**
 * 管理台用户长期记忆 API。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/memory")
public class AdminMemoryController {

    private final AdminMemoryService adminMemoryService;

    public AdminMemoryController(AdminMemoryService adminMemoryService) {
        this.adminMemoryService = adminMemoryService;
    }

    @GetMapping("/profiles")
    public Mono<ApiResponse<AdminProfilePage>> listProfiles(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String memoryKey,
            @RequestParam(required = false) String memoryType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createTimeFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createTimeTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminMemoryService.listProfiles(
                        userId, memoryKey, memoryType, status, createTimeFrom, createTimeTo, page, size)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/profiles")
    public Mono<ApiResponse<AdminProfileView>> createProfile(@RequestBody AdminProfileCreateRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminMemoryService.createProfile(op, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/profiles/{memoryId}")
    public Mono<ApiResponse<AdminProfileView>> updateProfile(
            @PathVariable String memoryId,
            @RequestBody AdminProfileUpdateRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminMemoryService.updateProfile(op, memoryId, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @DeleteMapping("/profiles/{memoryId}")
    public Mono<ApiResponse<Void>> deleteProfile(@PathVariable String memoryId) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> {
                    adminMemoryService.deleteProfile(op, memoryId);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/semantics")
    public Mono<ApiResponse<AdminSemanticPage>> listSemantics(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String similarQuery,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createTimeFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createTimeTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminMemoryService.listSemantics(
                        userId, status, keyword, similarQuery, createTimeFrom, createTimeTo, page, size)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/semantics/{id}")
    public Mono<ApiResponse<AdminSemanticView>> updateSemantic(
            @PathVariable String id,
            @RequestBody AdminSemanticUpdateRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminMemoryService.updateSemantic(op, id, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @DeleteMapping("/semantics/{id}")
    public Mono<ApiResponse<Void>> deleteSemantic(@PathVariable String id) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> {
                    adminMemoryService.deleteSemantic(op, id);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
