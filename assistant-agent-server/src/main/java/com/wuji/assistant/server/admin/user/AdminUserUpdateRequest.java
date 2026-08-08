package com.wuji.assistant.server.admin.user;

/**
 * 更新管理员请求（字段可选）。
 *
 * @param displayName 展示名
 * @param role        角色（builtin 禁止改）
 * @param status      ACTIVE / DISABLED（builtin 禁止改）
 * @author liudy
 */
public record AdminUserUpdateRequest(
        String displayName,
        String role,
        String status
) {
}
