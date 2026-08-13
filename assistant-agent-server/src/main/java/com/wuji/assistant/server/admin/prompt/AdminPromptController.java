package com.wuji.assistant.server.admin.prompt;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.server.admin.security.CurrentAdmin;
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

import java.util.List;

/**
 * 管理台提示词 API（草稿 / 发布 / 回滚 / Diff）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/prompts")
public class AdminPromptController {

    private final AdminPromptService adminPromptService;

    public AdminPromptController(AdminPromptService adminPromptService) {
        this.adminPromptService = adminPromptService;
    }

    @GetMapping
    public Mono<ApiResponse<List<AdminPromptSummary>>> list(
            @RequestParam(required = false) String group) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminPromptService.listSummaries(group)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{code:.+}")
    public Mono<ApiResponse<List<AdminPromptVersionView>>> versions(@PathVariable String code) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminPromptService.listVersions(code)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{code:.+}/versions/{fromVersion}/diff/{toVersion}")
    public Mono<ApiResponse<AdminPromptDiffView>> diff(
            @PathVariable String code,
            @PathVariable int fromVersion,
            @PathVariable int toVersion) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminPromptService.diff(code, fromVersion, toVersion)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/{code:.+}/versions")
    public Mono<ApiResponse<AdminPromptVersionView>> saveDraft(
            @PathVariable String code,
            @RequestBody AdminPromptVersionCreateRequest request) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminPromptService.saveDraft(admin, code, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/{code:.+}/versions/{version}/publish")
    public Mono<ApiResponse<AdminPromptVersionView>> publish(
            @PathVariable String code,
            @PathVariable int version) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminPromptService.publish(admin, code, version)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/{code:.+}/versions/{version}/rollback")
    public Mono<ApiResponse<AdminPromptVersionView>> rollback(
            @PathVariable String code,
            @PathVariable int version) {
        return CurrentAdmin.require().flatMap(admin ->
                Mono.fromCallable(() -> ApiResponse.ok(adminPromptService.rollback(admin, code, version)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }
}
