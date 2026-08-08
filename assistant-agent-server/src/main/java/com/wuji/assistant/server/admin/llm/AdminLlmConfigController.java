package com.wuji.assistant.server.admin.llm;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.server.admin.security.CurrentAdmin;
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

/**
 * 管理台 LLM 配置 API。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/llm-configs")
public class AdminLlmConfigController {

    private final AdminLlmConfigService adminLlmConfigService;

    public AdminLlmConfigController(AdminLlmConfigService adminLlmConfigService) {
        this.adminLlmConfigService = adminLlmConfigService;
    }

    @GetMapping
    public Mono<ApiResponse<AdminLlmConfigPage>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String modelKind,
            @RequestParam(required = false) String status) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(
                                adminLlmConfigService.list(admin, modelKind, status, page, size)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/{configId}")
    public Mono<ApiResponse<AdminLlmConfigView>> get(
            @PathVariable String configId,
            @RequestParam(defaultValue = "false") boolean revealKey) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(
                                adminLlmConfigService.get(admin, configId, revealKey)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping
    public Mono<ApiResponse<AdminLlmConfigView>> create(@RequestBody AdminLlmConfigCreateRequest request) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminLlmConfigService.create(admin, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/{configId}")
    public Mono<ApiResponse<AdminLlmConfigView>> update(
            @PathVariable String configId,
            @RequestBody AdminLlmConfigUpdateRequest request) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(
                                adminLlmConfigService.update(admin, configId, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @DeleteMapping("/{configId}")
    public Mono<ApiResponse<Void>> delete(@PathVariable String configId) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> {
                    adminLlmConfigService.delete(admin, configId);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
