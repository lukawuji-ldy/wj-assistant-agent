package com.wuji.assistant.agent.dto;

/**
 * 流式聊天请求体。
 *
 * @param conversationId 会话业务键，可空（空则服务端创建）
 * @param message        用户输入（续传时可空）
 * @param agentId        Agent 标识，可空
 * @param collection     知识库 collection，首期可空
 * @param streamId       续传时带上服务端 meta 下发的流 id
 * @param lastEventId    客户端已收到的最大 SSE eventId（亦可由 Last-Event-ID 头传入）
 * @author liudy
 */
public record ChatStreamRequest(
        String conversationId,
        String message,
        String agentId,
        String collection,
        String streamId,
        Long lastEventId
) {
}
