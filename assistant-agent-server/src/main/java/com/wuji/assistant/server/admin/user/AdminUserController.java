package com.wuji.assistant.server.admin.user;

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
 * 后台用户管理 API。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 分页列表。
     *
     * @param page 页码
     * @param size 页大小
     * @return 分页
     */
    @GetMapping
    public Mono<ApiResponse<AdminUserPage>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminUserService.list(page, size)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 详情。
     *
     * @param adminId 业务键
     * @return 视图
     */
    @GetMapping("/{adminId}")
    public Mono<ApiResponse<AdminUserView>> get(@PathVariable String adminId) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminUserService.get(adminId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 创建。
     *
     * @param request 请求
     * @return 新建视图
     */
    @PostMapping
    public Mono<ApiResponse<AdminUserView>> create(@RequestBody AdminUserCreateRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminUserService.create(op, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 更新资料/角色/状态。
     *
     * @param adminId 业务键
     * @param request 请求
     * @return 更新后视图
     */
    @PutMapping("/{adminId}")
    public Mono<ApiResponse<AdminUserView>> update(
            @PathVariable String adminId,
            @RequestBody AdminUserUpdateRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> ApiResponse.ok(adminUserService.update(op, adminId, request)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 改密。
     *
     * @param adminId 业务键
     * @param request 新密码
     * @return OK
     */
    @PutMapping("/{adminId}/password")
    public Mono<ApiResponse<Void>> changePassword(
            @PathVariable String adminId,
            @RequestBody AdminPasswordChangeRequest request) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> {
                    adminUserService.changePassword(op, adminId, request);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 禁用（软删）。
     *
     * @param adminId 业务键
     * @return OK
     */
    @DeleteMapping("/{adminId}")
    public Mono<ApiResponse<Void>> delete(@PathVariable String adminId) {
        return CurrentAdmin.require().flatMap(op ->
                Mono.fromCallable(() -> {
                    adminUserService.delete(op, adminId);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
