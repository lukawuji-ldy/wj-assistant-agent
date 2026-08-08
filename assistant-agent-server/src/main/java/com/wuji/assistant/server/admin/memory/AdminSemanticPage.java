package com.wuji.assistant.server.admin.memory;

import java.util.List;

/**
 * Semantic 分页。
 *
 * @author liudy
 */
public record AdminSemanticPage(List<AdminSemanticView> items, long total, int page, int size) {
}
