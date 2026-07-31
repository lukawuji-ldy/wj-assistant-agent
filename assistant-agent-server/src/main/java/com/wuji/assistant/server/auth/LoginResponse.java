package com.wuji.assistant.server.auth;

/**
 * 登录响应。
 *
 * @param accessToken JWT
 * @param tokenType   固定 Bearer
 * @param userId      用户业务键
 * @param username    登录名
 * @param nickname    昵称
 * @param role        角色
 * @author liudy
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        String userId,
        String username,
        String nickname,
        String role
) {
}
