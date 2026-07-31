package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.rag.KnowledgeRetrievalToolFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将 KnowledgeRetrievalTool 暴露给 AgentFactory。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "wuji.rag", name = "as-tool", havingValue = "true", matchIfMissing = true)
public class DefaultRagToolProvider implements RagToolProvider {

    private final List<ToolCallback> tools;

    public DefaultRagToolProvider(KnowledgeRetrievalToolFactory toolFactory, WujiRagProperties ragProperties) {
        this.tools = List.of(toolFactory.create(ragProperties.getTopK(), ragProperties.getMinReliableScore()));
    }

    @Override
    public List<ToolCallback> getTools() {
        return tools;
    }
}
