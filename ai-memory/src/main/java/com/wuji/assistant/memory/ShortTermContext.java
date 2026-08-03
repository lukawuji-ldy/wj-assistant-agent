package com.wuji.assistant.memory;

import com.wuji.assistant.memory.model.ChatMessage;

import java.util.List;

/**
 * 短期记忆加载结果（含可选会话摘要）。
 *
 * @param summaryJson conversation.summary，可为 null
 * @param messages    watermark 之后窗口消息（正序）
 * @author liudy
 */
public record ShortTermContext(String summaryJson, List<ChatMessage> messages) {
}
