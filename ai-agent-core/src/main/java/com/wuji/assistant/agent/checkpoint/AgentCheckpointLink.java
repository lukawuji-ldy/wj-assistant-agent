package com.wuji.assistant.agent.checkpoint;

import java.util.UUID;

/**
 * 一次 {@code knowledge_search} 挂到父 Checkpoint 上的链接上下文。
 * 放在父 {@code RunnableConfig} metadata 里，工具返回后删除。
 *
 * @param parentThreadName  主会话 {@code userId:conversationId}
 * @param parentToolCallId  父图里这次工具调用的 id
 * @param childThreadName   子 Agent 线程名，形如 {@code kb:{uuid}}
 * @author liudy
 */
public record AgentCheckpointLink(
        String parentThreadName,
        String parentToolCallId,
        String childThreadName
) {

    public static final String META_KEY = "wuji.agentCheckpointLink";

    /**
     * 拦截器从 metadata 读取父线程名。与 {@code RunnableConfig.threadId} 相同。
     */
    public static final String PARENT_THREAD_KEY = "wuji.agentCheckpointParentThread";

    public static final String CHILD_PREFIX = "kb:";

    public static final String KIND_SUB_AGENT = "SUB_AGENT";

    public static final String KIND_RAG = "RAG";

    /**
     * @return 新的子线程名
     */
    public static String newChildThreadName() {
        return CHILD_PREFIX + UUID.randomUUID();
    }
}
