package com.wuji.assistant.agent.kbtest;

/**
 * 知识库测试执行状态。
 *
 * @author liudy
 */
public enum KbTestStatus {

    /** 全部领域成功 */
    SUCCEEDED,

    /** 部分领域失败，其余成功 */
    PARTIAL,

    /** 主 Agent 失败或超时 */
    FAILED
}
