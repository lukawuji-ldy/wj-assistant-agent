package com.wuji.assistant.memory.retrieve;

import com.wuji.assistant.memory.model.UserSemanticHit;

import java.util.List;

/**
 * 用户语义记忆检索端口（Embedding 实现放在 agent-core，避免 ai-memory 依赖 ai-rag）。
 *
 * @author liudy
 */
public interface SemanticMemorySearchPort {

    /**
     * 按问句向量召回 ACTIVE 语义记忆。
     *
     * @param userId   用户
     * @param query    问句原文
     * @param topK     条数
     * @param minScore 最低余弦分
     * @return 命中；不可用时返回空列表
     */
    List<UserSemanticHit> search(String userId, String query, int topK, double minScore);
}
