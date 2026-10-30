package com.wuji.assistant.agent.memory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 同一会话同时只允许一个摘要压缩任务。
 *
 * @author liudy
 */
public final class ConversationCompressGate {

    private final ConcurrentHashMap<String, Boolean> running = new ConcurrentHashMap<>();

    /**
     * @param conversationId 会话
     * @return 是否拿到执行权
     */
    public boolean tryAcquire(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        return running.putIfAbsent(conversationId.trim(), Boolean.TRUE) == null;
    }

    /**
     * @param conversationId 会话
     */
    public void release(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        running.remove(conversationId.trim());
    }
}
