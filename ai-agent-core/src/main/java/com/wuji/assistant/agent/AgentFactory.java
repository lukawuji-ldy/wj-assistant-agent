package com.wuji.assistant.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitExceededException;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitExceededException;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.wuji.assistant.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.model.SwallowedLlmErrorInterceptor;
import com.wuji.assistant.agent.prompt.PromptTemplateChangedEvent;
import com.wuji.assistant.agent.prompt.WujiSystemPromptInterceptor;
import com.wuji.assistant.agent.tool.BuiltinToolProvider;
import com.wuji.assistant.agent.tool.KnowledgeSearchLinkInterceptor;
import com.wuji.assistant.agent.tool.McpToolHashChangedEvent;
import com.wuji.assistant.agent.tool.McpToolProvider;
import com.wuji.assistant.agent.tool.RagToolProvider;
import com.wuji.assistant.agent.token.TokenBudgetEnforcer;
import com.wuji.assistant.agent.token.TokenBudgetInterceptor;
import com.wuji.assistant.agent.token.TokenCounter;
import com.wuji.assistant.agent.token.TokenCounterRegistry;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 llm configId 构建并缓存有界 ReactAgent（PostgresSaver / MemorySaver）。
 * 图与 Saver 共用 {@link CheckpointSaverFactory#graphSerializer()}。
 *
 * @author liudy
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final LlmClientFactory llmClientFactory;
    private final WujiAgentProperties agentProperties;
    private final CheckpointSaverFactory checkpointSaverFactory;
    private final BuiltinToolProvider builtinToolProvider;
    private final RagToolProvider ragToolProvider;
    private final McpToolProvider mcpToolProvider;
    private final TokenBudgetEnforcer enforcer;
    private final ConcurrentHashMap<String, ReactAgent> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> toolSchemaTokens = new ConcurrentHashMap<>();

    public AgentFactory(LlmClientFactory llmClientFactory,
                        WujiAgentProperties agentProperties,
                        CheckpointSaverFactory checkpointSaverFactory,
                        BuiltinToolProvider builtinToolProvider,
                        RagToolProvider ragToolProvider,
                        McpToolProvider mcpToolProvider,
                        TokenBudgetEnforcer enforcer) {
        this.llmClientFactory = llmClientFactory;
        this.agentProperties = agentProperties;
        this.checkpointSaverFactory = checkpointSaverFactory;
        this.builtinToolProvider = builtinToolProvider;
        this.ragToolProvider = ragToolProvider;
        this.mcpToolProvider = mcpToolProvider;
        this.enforcer = enforcer;
    }

    /**
     * 获取或创建指定配置的 ReactAgent。
     *
     * @param configId llm_config.config_id
     * @return ReactAgent
     */
    public ReactAgent getOrCreate(String configId) {
        if (!StringUtils.hasText(configId)) {
            throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "configId 不能为空");
        }
        return cache.computeIfAbsent(configId, this::build);
    }

    /**
     * 使缓存失效。
     *
     * @param configId 配置键
     */
    public void invalidate(String configId) {
        cache.remove(configId);
        toolSchemaTokens.remove(configId);
        log.info("ReactAgent cache invalidated, configId={}", configId);
    }

    /**
     * 构建 Agent 时按 configId 计一次工具 schema，随 Agent 缓存失效。
     *
     * @param configId 配置键
     * @return 工具定义 token
     */
    public int toolSchemaTokens(String configId) {
        getOrCreate(configId);
        return toolSchemaTokens.getOrDefault(configId, 0);
    }

    @EventListener
    public void onMcpToolHashChanged(McpToolHashChangedEvent event) {
        cache.clear();
        toolSchemaTokens.clear();
        log.info("ReactAgent cache cleared due to MCP toolHash change: {}", event.toolHash());
    }

    @EventListener
    public void onPromptTemplateChanged(PromptTemplateChangedEvent event) {
        cache.clear();
        toolSchemaTokens.clear();
        log.info("ReactAgent cache cleared due to prompt change: {}", event.code());
    }

    /**
     * 将框架超限异常映射为业务错误码。
     *
     * @param ex 原始异常
     * @return 若为超限则包装后的异常，否则原样返回
     */
    public static RuntimeException mapLimitException(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof ModelCallLimitExceededException
                    || cur instanceof ToolCallLimitExceededException) {
                return new WujiException(ErrorCode.AGENT_MAX_ITERATIONS,
                        ErrorCode.AGENT_MAX_ITERATIONS.getMessage(), cur);
            }
            cur = cur.getCause();
        }
        if (ex instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new WujiException(ErrorCode.INTERNAL_ERROR,
                ex.getMessage() == null ? ErrorCode.INTERNAL_ERROR.getMessage() : ex.getMessage(), ex);
    }

    private ReactAgent build(String configId) {
        ChatModel chatModel = llmClientFactory.getChatModel(configId);
        List<ToolCallback> tools = aggregateTools();
        TokenCounter counter = TokenCounterRegistry.forConfig(configId);
        int schemaTokens = counter.countTools(toolDefinitions(tools));
        toolSchemaTokens.put(configId, schemaTokens);
        int maxModelCalls = Math.max(1, agentProperties.getMaxModelCalls());
        int maxToolRounds = Math.max(1, agentProperties.getMaxToolRounds());
        BaseCheckpointSaver saver = checkpointSaverFactory.getSaver();

        ModelCallLimitHook modelLimit = ModelCallLimitHook.builder()
                .runLimit(maxModelCalls)
                .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
                .build();
        ToolCallLimitHook toolLimit = ToolCallLimitHook.builder()
                .runLimit(maxToolRounds)
                .exitBehavior(ToolCallLimitHook.ExitBehavior.ERROR)
                .build();

        String agentName = (StringUtils.hasText(agentProperties.getId())
                ? agentProperties.getId() : "default") + "-" + configId;

        var builder = ReactAgent.builder()
                .name(agentName)
                .model(chatModel)
                .saver(saver)
                .stateSerializer(CheckpointSaverFactory.graphSerializer())
                .hooks(modelLimit, toolLimit)
                .interceptors(interceptors(configId, schemaTokens).toArray(Interceptor[]::new));
        if (!tools.isEmpty()) {
            builder.tools(tools);
        }
        ReactAgent agent = builder.build();
        log.info("ReactAgent initialized, name={}, configId={}, tools={}, maxModelCalls={}, maxToolRounds={}",
                agentName, configId, tools.size(), maxModelCalls, maxToolRounds);
        return agent;
    }

    private List<Interceptor> interceptors(String configId, int schemaTokens) {
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.add(new WujiSystemPromptInterceptor());
        interceptors.add(new SwallowedLlmErrorInterceptor());
        interceptors.add(new TokenBudgetInterceptor(configId, schemaTokens, enforcer));
        interceptors.add(new KnowledgeSearchLinkInterceptor());
        return interceptors;
    }

    private static List<ToolDefinition> toolDefinitions(List<ToolCallback> tools) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (ToolCallback tool : tools) {
            if (tool == null) {
                continue;
            }
            try {
                ToolDefinition definition = tool.getToolDefinition();
                if (definition != null) {
                    definitions.add(definition);
                }
            } catch (RuntimeException ignored) {
                // 单个工具定义读失败不影响其余工具计数
            }
        }
        return definitions;
    }

    private List<ToolCallback> aggregateTools() {
        List<ToolCallback> tools = new ArrayList<>();
        tools.addAll(nullToEmpty(builtinToolProvider.getTools()));
        tools.addAll(nullToEmpty(ragToolProvider.getTools()));
        tools.addAll(nullToEmpty(mcpToolProvider.getTools()));
        return tools;
    }

    private static List<ToolCallback> nullToEmpty(List<ToolCallback> list) {
        return list == null ? List.of() : list;
    }
}
