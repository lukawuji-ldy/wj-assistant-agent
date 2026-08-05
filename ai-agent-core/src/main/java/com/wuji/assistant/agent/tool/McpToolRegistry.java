package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP 工具目录注册表：
 * - 通过 Server `/mcp/info` 的 toolHash 判断目录是否变更
 * - 仅在 hash 变化时重新 discover tools
 * - 对外暴露最终可注入到 Agent 的 tools 子集（allowlist 裁剪）
 */
@Component
@ConditionalOnProperty(prefix = "wuji.mcp", name = "enabled", havingValue = "true", matchIfMissing = false)
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private static final Duration INFO_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DISCOVER_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(60);

    private final WujiMcpProperties mcpProperties;
    private final ToolCallbackProviderDiscovery discovery;
    private final WebClient webClient;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicReference<String> lastToolHash = new AtomicReference<>("");
    private final AtomicReference<List<ToolCallback>> activeTools = new AtomicReference<>(List.of());

    private final Set<String> includeTools;

    public McpToolRegistry(WujiMcpProperties mcpProperties,
                             ToolCallbackProviderDiscovery discovery,
                             ApplicationEventPublisher eventPublisher) {
        this.mcpProperties = mcpProperties;
        this.discovery = discovery;
        this.eventPublisher = eventPublisher;
        this.includeTools = toIncludeToolSet(mcpProperties.getIncludeTools());

        WebClient.Builder builder = WebClient.builder().baseUrl(mcpProperties.getServerUrl());
        if (mcpProperties.getAuth() != null && mcpProperties.getAuth().isEnabled()) {
            String apiKey = mcpProperties.getAuth().getApiKey();
            if (StringUtils.hasText(apiKey)) {
                builder.defaultHeader("Authorization", "Bearer " + apiKey);
            } else {
                log.warn("wuji.mcp.auth.enabled=true but api-key empty; /mcp/info calls may get 401");
            }
        }
        this.webClient = builder.build();
    }

    @PostConstruct
    public void init() {
        refreshIfHashChanged(true);
    }

    @Scheduled(fixedDelay = 60000)
    public void periodicRefresh() {
        refreshIfHashChanged(false);
    }

    public List<ToolCallback> getTools() {
        // 兜底：首次调用若初始化失败仍返回当前缓存（默认空列表）。
        return activeTools.get();
    }

    private void refreshIfHashChanged(boolean firstAttempt) {
        McpInfo info;
        try {
            info = fetchMcpInfo();
        } catch (Exception ex) {
            if (firstAttempt) {
                log.warn("MCP /mcp/info fetch failed during init, continuing with empty tools: {}", ex.toString());
            } else {
                log.warn("MCP /mcp/info fetch failed, keeping previous tools: {}", ex.toString());
            }
            return;
        }
        if (info == null || !StringUtils.hasText(info.toolHash())) {
            log.warn("MCP /mcp/info returned empty toolHash; keeping previous tools");
            return;
        }

        String oldHash = lastToolHash.get();
        if (!firstAttempt && Objects.equals(info.toolHash(), oldHash) && !activeTools.get().isEmpty()) {
            return;
        }

        // toolHash 变化：重新 discover tools，并应用 allowlist 裁剪。
        List<ToolCallback> discovered;
        try {
            discovered = discovery.discoverTools();
        } catch (Exception ex) {
            log.warn("MCP tool discover failed, keeping previous tools: {}", ex.toString());
            return;
        }
        List<ToolCallback> filtered = applyAllowlist(discovered);
        if (filtered.isEmpty() && !includeTools.isEmpty()) {
            log.warn("MCP allowlist matched no tools, includeTools={}, server={}", includeTools, mcpProperties.getServerUrl());
        }
        boolean toolHashChanged = !Objects.equals(info.toolHash(), oldHash);
        activeTools.set(List.copyOf(filtered));
        lastToolHash.set(info.toolHash());

        // toolHash 发生变化：通知 Agent 侧缓存失效
        if (toolHashChanged) {
            eventPublisher.publishEvent(new McpToolHashChangedEvent(info.toolHash()));
        }
        log.info("MCP tools refreshed, server={}, toolHash={}, totalTools={}, allowedTools={}",
                mcpProperties.getServerUrl(),
                info.toolHash(),
                discovered.size(),
                filtered.size());
    }

    /**
     * 拉取 `/mcp/info`，用于比较 toolHash 并决定是否刷新 tools。
     * <p>
     * 供单元测试覆写，避免真实 HTTP 依赖。
     */
    protected McpInfo fetchMcpInfo() {
        return webClient.get()
                .uri("/mcp/info")
                .retrieve()
                .bodyToMono(McpInfo.class)
                .timeout(INFO_TIMEOUT)
                .block();
    }

    private List<ToolCallback> applyAllowlist(List<ToolCallback> discovered) {
        if (includeTools.isEmpty()) {
            return discovered;
        }
        List<ToolCallback> filtered = new ArrayList<>();
        for (ToolCallback cb : discovered) {
            if (cb == null || cb.getToolDefinition() == null) {
                continue;
            }
            ToolDefinition def = cb.getToolDefinition();
            String name = def.name();
            if (includeTools.contains(name)) {
                filtered.add(cb);
            }
        }
        return filtered;
    }

    private static Set<String> toIncludeToolSet(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Set.of();
        }
        Set<String> set = new HashSet<>();
        for (String v : list) {
            if (StringUtils.hasText(v)) {
                set.add(v.trim());
            }
        }
        return set.isEmpty() ? Set.of() : Set.copyOf(set);
    }

    /**
     * 把旧 ClientMcpToolProvider 的 discover 逻辑抽出来，供 McpToolRegistry 使用。
     */
    @Component
    static class ToolCallbackProviderDiscovery {

        private final org.springframework.beans.factory.ObjectProvider<ToolCallbackProvider> providers;
        private final WujiMcpProperties mcpProperties;

        ToolCallbackProviderDiscovery(org.springframework.beans.factory.ObjectProvider<ToolCallbackProvider> providers,
                                       WujiMcpProperties mcpProperties) {
            this.providers = providers;
            this.mcpProperties = mcpProperties;
        }

        List<ToolCallback> discoverTools() {
            List<ToolCallback> collected = new ArrayList<>();
            Set<String> names = new HashSet<>();
            List<ToolCallbackProvider> mcpProviders = new ArrayList<>();
            for (ToolCallbackProvider provider : providers) {
                if (provider != null && ClientMcpToolProvider.isMcpProvider(provider)) {
                    mcpProviders.add(provider);
                }
            }

            // Sync 优先，避免 Sync+Async 双注册时 Async 覆盖；同名保留先到者
            mcpProviders.sort(Comparator.comparingInt(ClientMcpToolProvider::mcpProviderOrder));

            for (ToolCallbackProvider provider : mcpProviders) {
                String cn = provider.getClass().getName();
                ToolCallback[] callbacks;
                try {
                    callbacks = provider.getToolCallbacks();
                } catch (Exception ex) {
                    logProviderWarn("skip MCP provider " + cn, ex);
                    continue;
                }
                if (callbacks == null) {
                    continue;
                }
                for (ToolCallback cb : callbacks) {
                    if (cb == null || cb.getToolDefinition() == null) {
                        continue;
                    }
                    String name = cb.getToolDefinition().name();
                    if (!StringUtils.hasText(name)) {
                        continue;
                    }
                    if (!names.add(name)) {
                        logProviderWarn("skip duplicate MCP tool name=" + name + " from " + cn, null);
                        continue;
                    }
                    collected.add(cb);
                }
            }

            return List.copyOf(collected);
        }

        private static void logProviderWarn(String msg, Exception ex) {
            if (ex == null) {
                LoggerFactory.getLogger(ToolCallbackProviderDiscovery.class).warn(msg);
            } else {
                LoggerFactory.getLogger(ToolCallbackProviderDiscovery.class).warn("{}: {}", msg, ex.toString());
            }
        }
    }

    /**
     * `/mcp/info` 返回结构（Agent 侧只关心 toolHash）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record McpInfo(String toolHash) {
    }
}

