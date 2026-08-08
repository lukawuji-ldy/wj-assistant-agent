package com.wuji.assistant.server.admin.user;

/**
 * 管理员列表项 / 详情（不含密码）。
 *
 * @param adminId     业务键
 * @param username    登录名
 * @param displayName 展示名
 * @param role        SUPER_ADMIN / OPERATOR
 * @param status      ACTIVE / DISABLED
 * @param builtin     是否内置
 * @author liudy
 */
public record AdminUserView(
        String adminId,
        String username,
        String displayName,
        String role,
        String status,
        boolean builtin
) {
}
