package com.wuji.assistant.agent.checkpoint;

/**
 * 写入 {@code agent_checkpoint_link} 的一行。
 *
 * @param parentThreadName  父线程名
 * @param parentToolCallId  父工具调用 id
 * @param childThreadName   子线程名
 * @param kind              {@code SUB_AGENT} 或 {@code RAG}
 * @param domainName        领域
 * @param queryText         原问题
 * @param rewrittenQueries  改写问句 JSON，可空
 * @param hitCount          命中条数，可空
 * @param errorMessage      失败原因，最长 500
 * @author liudy
 */
public record AgentCheckpointLinkWrite(
        String parentThreadName,
        String parentToolCallId,
        String childThreadName,
        String kind,
        String domainName,
        String queryText,
        String rewrittenQueries,
        Integer hitCount,
        String errorMessage
) {
}
