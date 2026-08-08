package com.wuji.assistant.server.admin.log.checkpoint;

/**
 * Checkpoint messages 表格行。
 *
 * @param index         序号
 * @param role          USER / ASSISTANT / SYSTEM / TOOL
 * @param content       文本内容
 * @param toolCallsJson toolCalls JSON 文本
 * @param toolResponsesJson tool responses JSON 文本
 * @author liudy
 */
public record AdminCheckpointMessageView(
        int index,
        String role,
        String content,
        String toolCallsJson,
        String toolResponsesJson
) {
}
