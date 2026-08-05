package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 从 MCP Client 发现的 Tool 转为 ToolCallback。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "wuji.mcp", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ClientMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(ClientMcpToolProvider.class);

    private final McpToolRegistry mcpToolRegistry;

    public ClientMcpToolProvider(McpToolRegistry mcpToolRegistry,
                                 WujiMcpProperties mcpProperties) {
        this.mcpToolRegistry = mcpToolRegistry;
        log.info("ClientMcpToolProvider initialized, enabled={}, serverUrl={}, includeToolsCount={}",
                mcpProperties.isEnabled(),
                mcpProperties.getServerUrl(),
                mcpProperties.getIncludeTools() == null ? 0 : mcpProperties.getIncludeTools().size());
    }

    /**
     * SyncMcpToolCallbackProvider 优先于 Async，其它 MCP Provider 最后。
     *
     * @param provider MCP Provider
     * @return 排序权重（越小越优先）
     */
    static int mcpProviderOrder(ToolCallbackProvider provider) {
        String name = provider.getClass().getName();
        if (name.contains("SyncMcpToolCallbackProvider")) {
            return 0;
        }
        if (name.contains("AsyncMcpToolCallbackProvider")) {
            return 1;
        }
        return 2;
    }

    /**
     * 识别 Spring AI MCP Client 产出的 ToolCallbackProvider，排除本地 RAG/Method 等。
     *
     * @param provider 候选 Provider
     * @return 是否为 MCP 来源
     */
    static boolean isMcpProvider(ToolCallbackProvider provider) {
        Class<?> type = provider.getClass();
        while (type != null && type != Object.class) {
            String name = type.getName();
            if (name.startsWith("org.springframework.ai.mcp")
                    || name.contains("McpToolCallback")) {
                return true;
            }
            type = type.getSuperclass();
        }
        for (Class<?> iface : provider.getClass().getInterfaces()) {
            String name = iface.getName();
            if (name.startsWith("org.springframework.ai.mcp")
                    || name.contains("McpToolCallback")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ToolCallback> getTools() {
        return mcpToolRegistry.getTools();
    }
}
