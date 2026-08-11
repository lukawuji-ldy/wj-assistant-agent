package com.wuji.assistant.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 检索工具 description 可覆盖。
 *
 * @author liudy
 */
class KnowledgeRetrievalToolFactoryTest {

    @Test
    void createUsesProvidedDescription() {
        KnowledgeRetrievalToolFactory factory = new KnowledgeRetrievalToolFactory(null, new ObjectMapper());
        ToolCallback callback = factory.create(5, 0.5, "custom-desc");
        assertEquals("knowledge_retrieval", callback.getToolDefinition().name());
        assertEquals("custom-desc", callback.getToolDefinition().description());
    }

    @Test
    void createFallsBackWhenDescriptionBlank() {
        KnowledgeRetrievalToolFactory factory = new KnowledgeRetrievalToolFactory(null, new ObjectMapper());
        ToolCallback callback = factory.create(5, 0.5, "  ");
        assertEquals(KnowledgeRetrievalToolFactory.DEFAULT_DESCRIPTION,
                callback.getToolDefinition().description());
    }
}
