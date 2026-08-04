package com.wuji.assistant.memory.extract;

import org.springframework.util.StringUtils;

/**
 * 落库闸门：呼应 agent-memory §5.2（非法 PROFILE / 无 content 的 SEMANTIC 拒绝）。
 *
 * @author liudy
 */
public final class MemoryActionGate {

    private MemoryActionGate() {
    }

    /**
     * @param item 候选动作
     * @return true 可落库；false 应跳过
     */
    public static boolean accept(MemoryActionItem item) {
        if (item == null || item.action() == null) {
            return false;
        }
        if (item.action() == MemoryAction.IGNORE) {
            return false;
        }
        String type = item.resultType() == null ? "" : item.resultType().trim().toUpperCase();
        if ("SEMANTIC".equals(type)) {
            return item.action() == MemoryAction.INSERT || item.action() == MemoryAction.UPDATE
                    || item.action() == MemoryAction.MERGE
                    ? StringUtils.hasText(item.content())
                    : item.action() == MemoryAction.DELETE;
        }
        if ("PROFILE".equals(type) || "PREFERENCE".equals(type)) {
            if (item.action() == MemoryAction.DELETE) {
                return StringUtils.hasText(item.memoryKey());
            }
            if (item.action() == MemoryAction.INSERT
                    || item.action() == MemoryAction.UPDATE
                    || item.action() == MemoryAction.MERGE) {
                return StringUtils.hasText(item.memoryKey()) && StringUtils.hasText(item.memoryValue());
            }
            return false;
        }
        return false;
    }

    /**
     * 低置信度 PROFILE/PREFERENCE 降为不可落库（由调用方改 IGNORE 或跳过）。
     *
     * @param item          动作
     * @param minConfidence 阈值
     * @return true 应降级跳过画像写入
     */
    public static boolean belowMinConfidence(MemoryActionItem item, double minConfidence) {
        if (item == null || item.confidence() == null) {
            return false;
        }
        String type = item.resultType() == null ? "" : item.resultType().trim().toUpperCase();
        if (!"PROFILE".equals(type) && !"PREFERENCE".equals(type)) {
            return false;
        }
        return item.confidence() < minConfidence;
    }
}
