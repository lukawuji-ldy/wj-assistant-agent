package com.wuji.assistant.memory.retrieve;

/**
 * 长期记忆 Router 端口（rule 实现于本模块；hybrid 可在 agent-core 覆盖）。
 *
 * @author liudy
 */
public interface MemoryRoutePort {

    /**
     * 判定是否需要长期记忆及类型。
     *
     * @param query 用户本轮原文
     * @return 决策
     */
    MemoryRouteDecision route(String query);
}
