package com.wuji.assistant.server.admin.security;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * 从 SecurityContext 取当前管理员。
 *
 * @author liudy
 */
public final class CurrentAdmin {

    private CurrentAdmin() {
    }

    /**
     * 获取当前登录管理员；未认证则 UNAUTHORIZED。
     *
     * @return AdminAuthUser Mono
     */
    public static Mono<AdminAuthUser> require() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(AdminAuthUser.class::isInstance)
                .cast(AdminAuthUser.class)
                .switchIfEmpty(Mono.error(new WujiException(ErrorCode.UNAUTHORIZED)));
    }
}
