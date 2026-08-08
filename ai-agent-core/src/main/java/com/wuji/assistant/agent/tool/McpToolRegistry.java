package com.wuji.assistant.agent.tool;

import com.wuji.assistant.agent.config.WujiMcpProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP 工具目录注册表（P5.2 多 Server）：
 * - 按 ACTIVE 端点拉 /mcp/info，聚合 toolHash
 * - allowlist 按 server 解析后合并
 * - 跨 Server 同名 tool 注入前 fail-fast
 */
@Component
@ConditionalOnProperty(prefix = "wuji.mcp", name = "enabled", havingValue = "true", matchIfMissing = false)
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private static final Duration INFO_TIMEOUT = Duration.ofSeconds(10);

    private final WujiMcpProperties mcpProperties;
    private final ToolCallbackProviderDiscovery discovery;
    private final ApplicationEventPublisher eventPublisher;
    private final McpAllowlistSource allowlistSource;
    private final ObjectProvider<McpServerEndpointSource> endpointSource;

    private final AtomicReference<String> lastToolHash = new AtomicReference<>("");
    private final AtomicReference<List<ToolCallback>> activeTools = new AtomicReference<>(List.of());
    private final AtomicReference<McpAllowlist> allowlist = new AtomicReference<>(McpAllowlist.allowAll());
    private final AtomicReference<Map<String, String>> toolOwner = new AtomicReference<>(Map.of());

    public McpToolRegistry(WujiMcpProperties mcpProperties,
                           ToolCallbackProviderDiscovery discovery,
                           ApplicationEventPublisher eventPublisher,
                           McpAllowlistSource allowlistSource,
                           ObjectProvider<McpServerEndpointSource> endpointSource) {
        this.mcpProperties = mcpProperties;
        this.discovery = discovery;
        this.eventPublisher = eventPublisher;
        this.allowlistSource = allowlistSource;
        this.endpointSource = endpointSource;
        reloadAllowlistPolicies();
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
        return activeTools.get();
    }

    /**
     * 管理台更新 binding 后：重载 allowlist、强制 rediscover、始终失效 Agent 缓存。
     */
    public void reloadAllowlistAndRefresh() {
        reloadServersAndRefresh();
    }

    /**
     * P5.2：重载端点 allowlist + 强制刷新目录。
     */
    public void reloadServersAndRefresh() {
        reloadAllowlistPolicies();
        forceRefreshTools(true);
        log.info("MCP reloadServersAndRefresh done, tools={}", activeTools.get().size());
    }

    private void reloadAllowlistPolicies() {
        List<McpServerEndpoint> endpoints = resolveEndpoints();
        Map<String, String> owners = new HashMap<>();
        Set<String> allowedUnion = new HashSet<>();
        boolean anyRestrict = false;
        boolean allAllowAll = true;

        for (McpServerEndpoint ep : endpoints) {
            McpAllowlist policy = allowlistSource.resolve(ep.serverCode());
            if (policy.restrict()) {
                anyRestrict = true;
                allAllowAll = false;
                allowedUnion.addAll(policy.allowedNames());
            }
        }

        if (!anyRestrict || allAllowAll) {
            allowlist.set(McpAllowlist.allowAll());
        } else {
            // 有 restrict 的 server：合并 enabled；allowAll 的 server 工具名稍后从 /mcp/info 并入
            allowlist.set(McpAllowlist.only(allowedUnion));
        }
        toolOwner.set(Map.copyOf(owners));
    }

    private List<McpServerEndpoint> resolveEndpoints() {
        McpServerEndpointSource source = endpointSource.getIfAvailable();
        if (source != null) {
            List<McpServerEndpoint> list = source.listActiveEndpoints();
            if (list != null && !list.isEmpty()) {
                return list;
            }
        }
        String token = null;
        if (mcpProperties.getAuth() != null && mcpProperties.getAuth().isEnabled()
                && StringUtils.hasText(mcpProperties.getAuth().getApiKey())) {
            token = mcpProperties.getAuth().getApiKey();
        }
        return List.of(new McpServerEndpoint(
                McpAllowlistSource.DEFAULT_SERVER_CODE,
                mcpProperties.getServerUrl(),
                token));
    }

    private void refreshIfHashChanged(boolean firstAttempt) {
        AggregatedInfo info;
        try {
            info = fetchAggregatedInfo();
        } catch (Exception ex) {
            if (firstAttempt) {
                log.warn("MCP /mcp/info fetch failed during init, continuing with empty tools: {}", ex.toString());
            } else {
                log.warn("MCP /mcp/info fetch failed, keeping previous tools: {}", ex.toString());
            }
            return;
        }
        if (info == null || !StringUtils.hasText(info.combinedHash())) {
            log.warn("MCP /mcp/info returned empty toolHash; keeping previous tools");
            return;
        }

        String oldHash = lastToolHash.get();
        if (!firstAttempt && Objects.equals(info.combinedHash(), oldHash) && !activeTools.get().isEmpty()) {
            return;
        }

        applyDiscovered(info, oldHash, false);
    }

    private void forceRefreshTools(boolean publishAlways) {
        AggregatedInfo info;
        try {
            info = fetchAggregatedInfo();
        } catch (Exception ex) {
            log.warn("MCP force refresh /mcp/info failed, re-applying allowlist on cached tools: {}", ex.toString());
            List<ToolCallback> current = activeTools.get();
            if (current == null) {
                current = List.of();
            }
            List<ToolCallback> filtered = applyAllowlist(current);
            activeTools.set(List.copyOf(filtered));
            if (publishAlways) {
                eventPublisher.publishEvent(new McpToolHashChangedEvent(lastToolHash.get()));
            }
            return;
        }
        if (info == null || !StringUtils.hasText(info.combinedHash())) {
            log.warn("MCP force refresh empty toolHash; re-filtering cached tools");
            activeTools.set(List.copyOf(applyAllowlist(activeTools.get())));
            if (publishAlways) {
                eventPublisher.publishEvent(new McpToolHashChangedEvent(lastToolHash.get()));
            }
            return;
        }
        applyDiscovered(info, lastToolHash.get(), publishAlways);
    }

    private void applyDiscovered(AggregatedInfo info, String oldHash, boolean publishAlways) {
        mergeAllowAllToolNames(info);
        List<ToolCallback> discovered;
        try {
            discovered = discovery.discoverTools(true);
        } catch (IllegalStateException clash) {
            log.error("MCP tool name clash, refusing to assemble tools: {}", clash.getMessage());
            if (publishAlways) {
                eventPublisher.publishEvent(new McpToolHashChangedEvent(info.combinedHash()));
            }
            return;
        } catch (Exception ex) {
            log.warn("MCP tool discover failed, keeping previous tools: {}", ex.toString());
            if (publishAlways) {
                eventPublisher.publishEvent(new McpToolHashChangedEvent(info.combinedHash()));
            }
            return;
        }
        McpAllowlist policy = allowlist.get();
        List<ToolCallback> filtered = applyAllowlist(discovered);
        if (filtered.isEmpty() && policy.restrict()) {
            log.warn("MCP allowlist matched no tools, restrict={}, allowed={}",
                    policy.restrict(), policy.allowedNames());
        }
        boolean toolHashChanged = !Objects.equals(info.combinedHash(), oldHash);
        activeTools.set(List.copyOf(filtered));
        lastToolHash.set(info.combinedHash());

        if (publishAlways || toolHashChanged) {
            eventPublisher.publishEvent(new McpToolHashChangedEvent(info.combinedHash()));
        }
        log.info("MCP tools refreshed, endpoints={}, toolHash={}, totalTools={}, allowedTools={}, restrict={}",
                info.endpointCount(),
                info.combinedHash(),
                discovered.size(),
                filtered.size(),
                policy.restrict());
    }

    /**
     * 对 allowAll 的 server，把其 /mcp/info 工具名并入 restrict 并集，避免误杀他 server 全量工具。
     */
    private void mergeAllowAllToolNames(AggregatedInfo info) {
        List<McpServerEndpoint> endpoints = resolveEndpoints();
        Set<String> union = new HashSet<>();
        boolean anyRestrict = false;
        for (McpServerEndpoint ep : endpoints) {
            McpAllowlist policy = allowlistSource.resolve(ep.serverCode());
            if (policy.restrict()) {
                anyRestrict = true;
                union.addAll(policy.allowedNames());
            } else {
                List<String> names = info.toolsByServer().getOrDefault(ep.serverCode(), List.of());
                union.addAll(names);
            }
        }
        if (!anyRestrict) {
            allowlist.set(McpAllowlist.allowAll());
        } else {
            allowlist.set(McpAllowlist.only(union));
        }
        toolOwner.set(Map.copyOf(info.ownerByTool()));
    }

    protected AggregatedInfo fetchAggregatedInfo() {
        List<McpServerEndpoint> endpoints = resolveEndpoints();
        Map<String, List<String>> toolsByServer = new HashMap<>();
        Map<String, String> ownerByTool = new HashMap<>();
        StringBuilder hashBuf = new StringBuilder();
        for (McpServerEndpoint ep : endpoints) {
            McpInfo info = fetchMcpInfo(ep);
            if (info == null || !StringUtils.hasText(info.toolHash())) {
                throw new IllegalStateException("empty toolHash from " + ep.serverCode());
            }
            if (hashBuf.length() > 0) {
                hashBuf.append('|');
            }
            hashBuf.append(ep.serverCode()).append('=').append(info.toolHash());
            List<String> names = new ArrayList<>();
            if (info.tools() != null) {
                for (McpInfoTool t : info.tools()) {
                    if (t == null || !StringUtils.hasText(t.name())) {
                        continue;
                    }
                    String name = t.name().trim();
                    names.add(name);
                    String prev = ownerByTool.put(name, ep.serverCode());
                    if (prev != null && !prev.equals(ep.serverCode())) {
                        throw new IllegalStateException(
                                "MCP tool name clash across servers: " + name + " in " + prev + " and " + ep.serverCode());
                    }
                }
            }
            toolsByServer.put(ep.serverCode(), List.copyOf(names));
        }
        return new AggregatedInfo(hashBuf.toString(), endpoints.size(), toolsByServer, ownerByTool);
    }

    /**
     * 拉取单端点 {@code /mcp/info}。
     */
    protected McpInfo fetchMcpInfo(McpServerEndpoint endpoint) {
        WebClient.Builder builder = WebClient.builder().baseUrl(endpoint.baseUrl());
        if (StringUtils.hasText(endpoint.bearerToken())) {
            builder.defaultHeader("Authorization", "Bearer " + endpoint.bearerToken());
        }
        return builder.build()
                .get()
                .uri("/mcp/info")
                .retrieve()
                .bodyToMono(McpInfo.class)
                .timeout(INFO_TIMEOUT)
                .block();
    }

    /** 单测兼容：单 yml 端点。 */
    protected McpInfo fetchMcpInfo() {
        List<McpServerEndpoint> endpoints = resolveEndpoints();
        return fetchMcpInfo(endpoints.get(0));
    }

    private List<ToolCallback> applyAllowlist(List<ToolCallback> discovered) {
        McpAllowlist policy = allowlist.get();
        if (policy == null || !policy.restrict()) {
            return discovered == null ? List.of() : discovered;
        }
        Set<String> names = policy.allowedNames();
        List<ToolCallback> filtered = new ArrayList<>();
        for (ToolCallback cb : discovered) {
            if (cb == null || cb.getToolDefinition() == null) {
                continue;
            }
            ToolDefinition def = cb.getToolDefinition();
            String name = def.name();
            if (names.contains(name)) {
                filtered.add(cb);
            }
        }
        return filtered;
    }

    /**
     * 把旧 ClientMcpToolProvider 的 discover 逻辑抽出来，供 McpToolRegistry 使用。
     */
    @Component
    static class ToolCallbackProviderDiscovery {

        private final ObjectProvider<ToolCallbackProvider> providers;

        ToolCallbackProviderDiscovery(ObjectProvider<ToolCallbackProvider> providers) {
            this.providers = providers;
        }

        List<ToolCallback> discoverTools() {
            return discoverTools(false);
        }

        /**
         * @param failOnDuplicate true 时跨 provider 同名抛 IllegalStateException
         */
        List<ToolCallback> discoverTools(boolean failOnDuplicate) {
            List<ToolCallback> collected = new ArrayList<>();
            Set<String> names = new HashSet<>();
            List<ToolCallbackProvider> mcpProviders = new ArrayList<>();
            for (ToolCallbackProvider provider : providers) {
                if (provider != null && ClientMcpToolProvider.isMcpProvider(provider)) {
                    mcpProviders.add(provider);
                }
            }

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
                        if (failOnDuplicate) {
                            throw new IllegalStateException(
                                    "duplicate MCP tool name=" + name + " from " + cn);
                        }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record McpInfo(String toolHash, List<McpInfoTool> tools) {
        public McpInfo(String toolHash) {
            this(toolHash, List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record McpInfoTool(String name, String description) {
    }

    record AggregatedInfo(
            String combinedHash,
            int endpointCount,
            Map<String, List<String>> toolsByServer,
            Map<String, String> ownerByTool) {
    }
}
