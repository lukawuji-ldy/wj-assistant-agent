package com.wuji.assistant.agent.tool;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * RAG 工具提供者（本期空壳，下期接入 KnowledgeRetrievalTool）。
 *
 * @author liudy
 */
public interface RagToolProvider {

    /**
     * @return 工具回调列表
     */
    List<ToolCallback> getTools();
}
