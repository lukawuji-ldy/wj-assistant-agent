package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.rag.KnowledgeRetrievalToolFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 将 KnowledgeRetrievalTool 暴露给 AgentFactory；工具说明来自 prompt_template。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "wuji.rag", name = "as-tool", havingValue = "true", matchIfMissing = true)
public class DefaultRagToolProvider implements RagToolProvider {

    private final KnowledgeRetrievalToolFactory toolFactory;
    private final WujiRagProperties ragProperties;
    private final PromptTemplateService promptTemplateService;

    public DefaultRagToolProvider(KnowledgeRetrievalToolFactory toolFactory,
                                  WujiRagProperties ragProperties,
                                  PromptTemplateService promptTemplateService) {
        this.toolFactory = toolFactory;
        this.ragProperties = ragProperties;
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public List<ToolCallback> getTools() {
        String description = promptTemplateService.loadAndRender(
                ragProperties.getToolDescriptionPromptCode(),
                Map.of(),
                KnowledgeRetrievalToolFactory.DEFAULT_DESCRIPTION);
        return List.of(toolFactory.create(
                ragProperties.getTopK(),
                ragProperties.getEffectiveMinReliableScore(),
                description));
    }
}
