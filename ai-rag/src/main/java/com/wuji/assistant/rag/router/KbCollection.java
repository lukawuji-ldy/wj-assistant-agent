package com.wuji.assistant.rag.router;

/**
 * 知识库目录中的一行。领域列为空时，这一行不出现在 {@code knowledge_search} 里。
 *
 * @param collection        命名空间
 * @param description       库说明
 * @param domainName        领域名
 * @param domainDescription 领域短说明
 * @param llmConfigId       该领域子 Agent 的 CHAT 配置键
 * @author liudy
 */
public record KbCollection(
        String collection,
        String description,
        String domainName,
        String domainDescription,
        String llmConfigId
) {
    public KbCollection(String collection, String description) {
        this(collection, description, null, null, null);
    }
}
