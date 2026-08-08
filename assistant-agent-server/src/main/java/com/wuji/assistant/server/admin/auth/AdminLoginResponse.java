package com.wuji.assistant.server.admin.auth;

/**
 * 后台登录响应。
 *
 * @param accessToken JWT
 * @param tokenType   固定 Bearer
 * @param adminId     管理员业务键
 * @param username    登录名
 * @param displayName 展示名
 * @param role        角色
 * @author liudy
 */
public record AdminLoginResponse(
        String accessToken,
        String tokenType,
        String adminId,
        String username,
        String displayName,
        String role
) {
}
