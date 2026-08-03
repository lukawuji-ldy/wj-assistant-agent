package com.wuji.assistant.agent.observability;

import org.slf4j.MDC;

/**
 * 聊天链路 MDC：traceId / conversationId / userId / agentId。
 *
 * @author liudy
 */
public final class ChatMdc {

    public static final String TRACE_ID = "traceId";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String USER_ID = "userId";
    public static final String AGENT_ID = "agentId";

    private ChatMdc() {
    }

    /**
     * 写入 MDC。
     *
     * @param traceId        业务 trace
     * @param conversationId 会话
     * @param userId         用户
     * @param agentId        Agent
     */
    public static void put(String traceId, String conversationId, String userId, String agentId) {
        if (traceId != null) {
            MDC.put(TRACE_ID, traceId);
        }
        if (conversationId != null) {
            MDC.put(CONVERSATION_ID, conversationId);
        }
        if (userId != null) {
            MDC.put(USER_ID, userId);
        }
        if (agentId != null) {
            MDC.put(AGENT_ID, agentId);
        }
    }

    /**
     * 清理本链路键。
     */
    public static void clear() {
        MDC.remove(TRACE_ID);
        MDC.remove(CONVERSATION_ID);
        MDC.remove(USER_ID);
        MDC.remove(AGENT_ID);
    }
}
