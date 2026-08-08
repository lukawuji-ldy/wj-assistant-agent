package com.wuji.assistant.server.admin.memory;

import java.util.List;

/**
 * Profile 分页。
 *
 * @author liudy
 */
public record AdminProfilePage(List<AdminProfileView> items, long total, int page, int size) {
}
