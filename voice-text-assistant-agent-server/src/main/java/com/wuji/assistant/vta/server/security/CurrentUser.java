package com.wuji.assistant.vta.server.security;

import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Mono<AuthUser> require() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(AuthUser.class::isInstance)
                .cast(AuthUser.class)
                .switchIfEmpty(Mono.error(new WujiException(ErrorCode.UNAUTHORIZED)));
    }
}

