package com.wuji.assistant.server.admin.memory;

/**
 * Profile 创建请求。
 *
 * @param userId       聊天用户
 * @param memoryType   PROFILE|PREFERENCE
 * @param memoryKey    稳定键
 * @param memoryValue  值
 * @param confidence   可选 0~1
 * @param importance   可选 0~1
 * @author liudy
 */
public record AdminProfileCreateRequest(
        String userId,
        String memoryType,
        String memoryKey,
        String memoryValue,
        Double confidence,
        Double importance
) {
}
