package com.wuji.assistant.server.security;

import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * 从 SecurityContext 取当前用户。
 *
 * @author liudy
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 获取当前登录用户；未认证则 UNAUTHORIZED。
     *
     * @return AuthUser Mono
     */
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
