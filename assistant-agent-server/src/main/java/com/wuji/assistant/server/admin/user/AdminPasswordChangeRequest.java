package com.wuji.assistant.server.admin.user;

/**
 * 改密请求。
 *
 * @param newPassword 新明文密码
 * @author liudy
 */
public record AdminPasswordChangeRequest(String newPassword) {
}
