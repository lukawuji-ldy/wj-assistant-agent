package com.wuji.assistant.server.admin.memory;

/**
 * Profile 更新请求（原地 UPDATE）。
 *
 * @param memoryType  PROFILE|PREFERENCE
 * @param memoryKey   键
 * @param memoryValue 值
 * @param status      ACTIVE|INACTIVE|DELETED|EXPIRED
 * @param confidence  置信度
 * @param importance  重要度
 * @author liudy
 */
public record AdminProfileUpdateRequest(
        String memoryType,
        String memoryKey,
        String memoryValue,
        String status,
        Double confidence,
        Double importance
) {
}
