package com.wuji.assistant.memory.retrieve;

/**
 * 一条可按分丢弃的长期记忆。
 *
 * @param key     画像 key；语义记忆可空
 * @param section {@code profile} 或 {@code semantic}
 * @param text    不含栏目标题的一行正文
 * @param score   综合分，越高越优先保留
 * @author liudy
 */
public record MemoryItem(String key, String section, String text, double score) {
}
