package com.wuji.assistant.memory.extract;

/**
 * Extract 产出的动作。
 *
 * @param action     动作
 * @param resultType PROFILE|PREFERENCE|SEMANTIC|NONE
 * @param memoryKey  键
 * @param memoryValue 值
 * @author liudy
 */
public record MemoryActionItem(
        MemoryAction action,
        String resultType,
        String memoryKey,
        String memoryValue
) {
}
