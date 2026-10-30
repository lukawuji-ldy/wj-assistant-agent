package com.wuji.assistant.server.auth;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.server.security.CurrentUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final Scheduler dbScheduler;

    public AuthController(AuthService authService,
                          @Qualifier("dbScheduler") Scheduler dbScheduler) {
        this.authService = authService;
        this.dbScheduler = dbScheduler;
    }

    @PostMapping("/auth/login")
    public Mono<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        return Mono.fromCallable(() -> ApiResponse.ok(authService.login(request)))
                .subscribeOn(dbScheduler);
    }

    @PostMapping("/auth/logout")
    public Mono<ApiResponse<Void>> logout() {
        return Mono.just(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public Mono<ApiResponse<AuthUser>> me() {
        return CurrentUser.require().map(ApiResponse::ok);
    }
}
