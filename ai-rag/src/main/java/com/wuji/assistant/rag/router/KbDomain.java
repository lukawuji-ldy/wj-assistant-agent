package com.wuji.assistant.rag.router;

/**
 * 知识库领域。一份文档只属于一个领域，检索按领域覆盖该领域下全部文档。
 *
 * @param name         {@code kb_domain.domain_name}
 * @param description  给主 Agent 的短说明
 * @param llmConfigId  领域子 Agent 的 CHAT 配置
 * @author liudy
 */
public record KbDomain(String name, String description, String llmConfigId) {
}
