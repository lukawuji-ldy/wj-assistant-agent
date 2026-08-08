package com.wuji.assistant.server.admin.user;

/**
 * 创建管理员请求。
 *
 * @param username    登录名
 * @param password    明文密码
 * @param displayName 展示名
 * @param role        SUPER_ADMIN / OPERATOR
 * @author liudy
 */
public record AdminUserCreateRequest(
        String username,
        String password,
        String displayName,
        String role
) {
}
