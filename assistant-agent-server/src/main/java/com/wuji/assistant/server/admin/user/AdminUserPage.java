package com.wuji.assistant.server.admin.user;

import java.util.List;

/**
 * 分页列表。
 *
 * @param items 当前页
 * @param total 总数
 * @param page  页码（从 1）
 * @param size  页大小
 * @author liudy
 */
public record AdminUserPage(
        List<AdminUserView> items,
        long total,
        int page,
        int size
) {
}
