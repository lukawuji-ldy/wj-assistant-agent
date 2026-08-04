package com.wuji.assistant.memory.retrieve;

import java.util.Set;

/**
 * Memory Router 输出。
 *
 * @param needMemory  是否需要查长期记忆
 * @param memoryTypes PROFILE / PREFERENCE / SEMANTIC（大写）
 * @author liudy
 */
public record MemoryRouteDecision(boolean needMemory, Set<String> memoryTypes) {

    public static MemoryRouteDecision skip() {
        return new MemoryRouteDecision(false, Set.of());
    }

    public static MemoryRouteDecision of(Set<String> types) {
        if (types == null || types.isEmpty()) {
            return skip();
        }
        return new MemoryRouteDecision(true, Set.copyOf(types));
    }
}
