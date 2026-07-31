package com.wuji.assistant.agent.dto;

/**
 * 非流式对话结果。
 *
 * @param conversationId 会话业务键
 * @param messageId      助手消息业务键
 * @param userMessageId  用户消息业务键
 * @param traceId        链路追踪 ID
 * @param content        助手完整回复
 * @param modelId        实际使用的 llm_config.config_id
 * @author liudy
 */
public record ChatResult(
        String conversationId,
        String messageId,
        String userMessageId,
        String traceId,
        String content,
        String modelId
) {
}
