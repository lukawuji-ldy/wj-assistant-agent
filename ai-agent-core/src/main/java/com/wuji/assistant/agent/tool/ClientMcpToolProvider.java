package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 MCP Client 发现的 Tool 转为 ToolCallback。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "wuji.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClientMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(ClientMcpToolProvider.class);

    private final List<ToolCallback> tools;

    public ClientMcpToolProvider(ObjectProvider<ToolCallbackProvider> providers,
                                 WujiMcpProperties mcpProperties) {
        List<ToolCallback> collected = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ToolCallbackProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            String cn = provider.getClass().getName();
            if (!cn.toLowerCase().contains("mcp")) {
                continue;
            }
            ToolCallback[] callbacks;
            try {
                callbacks = provider.getToolCallbacks();
            } catch (Exception ex) {
                log.warn("skip MCP provider {}: {}", cn, ex.toString());
                continue;
            }
            if (callbacks == null) {
                continue;
            }
            for (ToolCallback cb : callbacks) {
                String name = cb.getToolDefinition().name();
                if (!names.add(name)) {
                    throw new WujiException(ErrorCode.INTERNAL_ERROR,
                            "MCP/本地工具重名: " + name);
                }
                collected.add(cb);
            }
        }
        this.tools = List.copyOf(collected);
        log.info("MCP tools loaded, enabled={}, count={}, url={}",
                mcpProperties.isEnabled(), tools.size(), mcpProperties.getServerUrl());
    }

    @Override
    public List<ToolCallback> getTools() {
        return tools;
    }
}
