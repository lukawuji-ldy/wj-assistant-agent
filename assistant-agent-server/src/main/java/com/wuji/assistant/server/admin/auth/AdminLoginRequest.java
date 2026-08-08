package com.wuji.assistant.server.admin.auth;

/**
 * 后台登录请求。
 *
 * @param username 用户名
 * @param password 明文密码
 * @author liudy
 */
public record AdminLoginRequest(String username, String password) {
}
