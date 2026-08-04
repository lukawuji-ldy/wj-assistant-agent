package com.wuji.assistant.memory.extract;

/**
 * Extract 产出的动作（规则或 LLM）。
 *
 * @param action                 动作
 * @param resultType             PROFILE|PREFERENCE|SEMANTIC|NONE
 * @param memoryKey              键（PROFILE/PREFERENCE）
 * @param memoryValue            值（PROFILE/PREFERENCE）
 * @param content                SEMANTIC 正文
 * @param confidence             置信度
 * @param importance             重要度
 * @param reason                 理由
 * @param embeddingVectorLiteral SEMANTIC 向量字面量（由编排层写入，如 {@code [0.1,0.2,...]}）
 * @author liudy
 */
public record MemoryActionItem(
        MemoryAction action,
        String resultType,
        String memoryKey,
        String memoryValue,
        String content,
        Double confidence,
        Double importance,
        String reason,
        String embeddingVectorLiteral
) {

    /**
     * 规则路径兼容构造（无置信度 / 语义字段）。
     */
    public MemoryActionItem(MemoryAction action, String resultType, String memoryKey, String memoryValue) {
        this(action, resultType, memoryKey, memoryValue, null, null, null, null, null);
    }

    /**
     * 附带向量字面量。
     *
     * @param literal pgvector 字面量
     * @return 新实例
     */
    public MemoryActionItem withEmbedding(String literal) {
        return new MemoryActionItem(action, resultType, memoryKey, memoryValue, content,
                confidence, importance, reason, literal);
    }
}
