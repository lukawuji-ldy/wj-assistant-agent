package com.wuji.assistant.memory.model;

import java.util.List;

/**
 * 记忆分页结果。
 *
 * @param items 当前页
 * @param total 总数
 * @param page  页码（从 1）
 * @param size  页大小
 * @param <T>   行类型
 * @author liudy
 */
public record MemoryPage<T>(List<T> items, long total, int page, int size) {
}
