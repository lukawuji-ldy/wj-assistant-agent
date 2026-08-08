package com.wuji.assistant.server.admin.auth;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.server.admin.security.CurrentAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 后台鉴权接口。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /**
     * 管理员登录。
     *
     * @param request 登录请求
     * @return Admin JWT
     */
    @PostMapping("/login")
    public Mono<ApiResponse<AdminLoginResponse>> login(@RequestBody AdminLoginRequest request) {
        return Mono.fromCallable(() -> ApiResponse.ok(adminAuthService.login(request)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 登出（无状态 JWT，客户端丢弃即可）。
     *
     * @return OK
     */
    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout() {
        return Mono.just(ApiResponse.ok(null));
    }

    /**
     * 当前管理员。
     *
     * @return AdminAuthUser
     */
    @GetMapping("/me")
    public Mono<ApiResponse<AdminAuthUser>> me() {
        return CurrentAdmin.require().map(ApiResponse::ok);
    }
}
