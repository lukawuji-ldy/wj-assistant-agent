package com.wuji.assistant.server.auth;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.server.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 鉴权接口。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 预置账号登录。
     *
     * @param request 登录请求
     * @return token 与用户信息
     */
    @PostMapping("/auth/login")
    public Mono<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        return Mono.fromCallable(() -> ApiResponse.ok(authService.login(request)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 登出（无状态 JWT，客户端丢弃 token 即可）。
     *
     * @return OK
     */
    @PostMapping("/auth/logout")
    public Mono<ApiResponse<Void>> logout() {
        return Mono.just(ApiResponse.ok(null));
    }

    /**
     * 当前用户。
     *
     * @return 用户信息
     */
    @GetMapping("/me")
    public Mono<ApiResponse<AuthUser>> me() {
        return CurrentUser.require().map(ApiResponse::ok);
    }
}
