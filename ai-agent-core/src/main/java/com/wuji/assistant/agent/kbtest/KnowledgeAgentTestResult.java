package com.wuji.assistant.agent.kbtest;

import java.util.List;

/**
 * 知识库 Agent 测试结果。
 *
 * @param status           SUCCEEDED | PARTIAL | FAILED
 * @param answer           主 Agent 最终答案；失败时为 null
 * @param parentThreadName 主线程名 {@code kb-test:{uuid}}
 * @param parentThreadId   主线程 ID；checkpoint 尚未落库时为 null
 * @param domains          自动识别到的领域
 * @param traces           各领域检索轨迹
 * @param warnings         降级/漂移/失败告警
 * @author liudy
 */
public record KnowledgeAgentTestResult(
        KbTestStatus status,
        String answer,
        String parentThreadName,
        String parentThreadId,
        List<String> domains,
        List<DomainTrace> traces,
        List<String> warnings
) {
}
