package com.wuji.assistant.agent.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG as-tool=false 时的空实现。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "wuji.rag", name = "as-tool", havingValue = "false")
public class EmptyRagToolProvider implements RagToolProvider {

    @Override
    public List<ToolCallback> getTools() {
        return List.of();
    }
}
