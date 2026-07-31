package com.wuji.assistant.common.auth;

/**
 * 当前登录用户（来自 JWT，禁止信任请求体 userId）。
 *
 * @param userId   业务用户键
 * @param username 登录名
 * @param nickname 昵称
 * @param tenantId 租户
 * @param role     角色
 * @author liudy
 */
public record AuthUser(
        String userId,
        String username,
        String nickname,
        String tenantId,
        String role
) {
}
