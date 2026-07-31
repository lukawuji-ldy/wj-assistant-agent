package com.wuji.assistant.server.auth;

/**
 * 登录请求。
 *
 * @param username 用户名
 * @param password 明文密码
 * @author liudy
 */
public record LoginRequest(String username, String password) {
}
