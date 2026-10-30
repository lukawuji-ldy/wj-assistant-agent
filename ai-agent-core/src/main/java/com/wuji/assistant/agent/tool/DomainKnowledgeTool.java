package com.wuji.assistant.agent.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLink;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLinkRepository;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLinkWrite;
import com.wuji.assistant.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.assistant.agent.checkpoint.ChildCheckpointRelease;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.agent.kbtest.KbTestTraceCollector;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.prompt.WujiSystemPromptInterceptor;
import com.wuji.assistant.agent.stream.CheckpointGraphCaller;
import com.wuji.assistant.agent.rag.RagQueryRewrite;
import com.wuji.assistant.agent.rag.RagQueryRewriteOrchestrator;
import com.wuji.assistant.agent.token.CallBudget;
import com.wuji.assistant.agent.token.InputBudgetCalculator;
import com.wuji.assistant.agent.token.TokenBudgetEnforcer;
import com.wuji.assistant.agent.token.TokenBudgetInterceptor;
import com.wuji.assistant.agent.token.SystemPromptHolder;
import com.wuji.assistant.agent.token.TokenCounter;
import com.wuji.assistant.agent.token.TokenCounterRegistry;
import com.wuji.assistant.agent.token.TurnRetrievalCache;
import com.wuji.assistant.rag.KnowledgeRetrievalService;
import com.wuji.assistant.rag.RetrievalResult;
import com.wuji.assistant.rag.diagnose.RetrievalTrace;
import com.wuji.assistant.rag.router.KbDomain;
import com.wuji.assistant.rag.router.KbDomainRepository;
import com.wuji.assistant.rag.vector.PgVectorScoreNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.scheduler.Scheduler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * 主 Agent 的 {@code knowledge_search}。模型只选领域，子 Agent 在该领域内检索。
 *
 * @author liudy
 */
@Component
public class DomainKnowledgeTool {

    private static final Logger log = LoggerFactory.getLogger(DomainKnowledgeTool.class);

    static final String TOOL_NAME = "knowledge_search";
    static final String SEARCH_TOOL = "search_domain";

    private final KbDomainRepository domainRepository;
    private final KnowledgeRetrievalService retrievalService;
    private final LlmClientFactory llmClientFactory;
    private final InputBudgetCalculator inputBudgetCalculator;
    private final TokenBudgetEnforcer enforcer;
    private final RagQueryRewriteOrchestrator queryRewriteOrchestrator;
    private final WujiAgentProperties agentProperties;
    private final WujiRagProperties ragProperties;
    private final WujiModelProperties modelProperties;
    private final PromptTemplateService promptTemplateService;
    private final Scheduler kbIngestScheduler;
    private final ObjectMapper objectMapper;
    private final CheckpointSaverFactory checkpointSaverFactory;
    private final AgentCheckpointLinkRepository linkRepository;

    public DomainKnowledgeTool(KbDomainRepository domains,
                               KnowledgeRetrievalService retrievalService,
                               LlmClientFactory llmClientFactory,
                               InputBudgetCalculator inputBudgetCalculator,
                               TokenBudgetEnforcer enforcer,
                               RagQueryRewriteOrchestrator queryRewriteOrchestrator,
                               WujiAgentProperties agentProperties,
                               WujiRagProperties ragProperties,
                               WujiModelProperties modelProperties,
                               PromptTemplateService promptTemplateService,
                               @Qualifier("kbIngestScheduler") Scheduler kbIngestScheduler,
                               ObjectMapper objectMapper,
                               CheckpointSaverFactory checkpointSaverFactory,
                               AgentCheckpointLinkRepository linkRepository) {
        this.domainRepository = domains;
        this.retrievalService = retrievalService;
        this.llmClientFactory = llmClientFactory;
        this.inputBudgetCalculator = inputBudgetCalculator;
        this.enforcer = enforcer;
        this.queryRewriteOrchestrator = queryRewriteOrchestrator;
        this.agentProperties = agentProperties;
        this.ragProperties = ragProperties;
        this.modelProperties = modelProperties;
        this.promptTemplateService = promptTemplateService;
        this.kbIngestScheduler = kbIngestScheduler;
        this.objectMapper = objectMapper;
        this.checkpointSaverFactory = checkpointSaverFactory;
        this.linkRepository = linkRepository;
    }

    public ToolCallback callback(String description) {
        String desc = description == null ? "" : description.trim();
        String full = desc + domainCatalog();
        return FunctionToolCallback.builder(TOOL_NAME,
                        (Request req, org.springframework.ai.chat.model.ToolContext ctx) ->
                                search(req, cacheOf(ctx), callBudgetOf(ctx), userOf(ctx), conversationOf(ctx),
                                        linkOf(ctx), collectorOf(ctx)))
                .description(full.isBlank() ? "按领域检索知识库，只返回结论。" : full)
                .inputType(Request.class)
                .build();
    }

    private String search(Request req, TurnRetrievalCache cache, CallBudget parentBudget,
                          String userId, String conversationId, AgentCheckpointLink link,
                          KbTestTraceCollector collector) {
        String query = req == null || req.query() == null ? "" : req.query().trim();
        String domain = req == null || req.domain() == null ? "" : req.domain().trim();
        TurnRetrievalCache store = cache == null ? new TurnRetrievalCache() : cache;
        String answerKey = TurnRetrievalCache.key(conversationId, query, domain, TurnRetrievalCache.DOMAIN_RESULT);
        String cached = store.get(answerKey);
        if (cached != null) {
            return cached;
        }
        DomainSpec spec = resolve(domain);
        if (spec == null) {
            return "未知领域，无法检索";
        }
        int remaining = parentBudget == null ? Integer.MAX_VALUE : parentBudget.remaining();
        if (remaining <= 0) {
            return "剩余上下文不足，无法检索";
        }
        String text = runSubAgent(spec, query, remaining, store, conversationId, userId, link, collector);
        store.put(answerKey, text);
        return text;
    }

    String searchDomain(DomainSpec spec, String query, TurnRetrievalCache cache,
                        String conversationId, String userId) {
        return searchDomain(spec, query, cache, conversationId, userId, null, null);
    }

    String searchDomain(DomainSpec spec, String query, TurnRetrievalCache cache,
                        String conversationId, String userId, AgentCheckpointLink link) {
        return searchDomain(spec, query, cache, conversationId, userId, link, null);
    }

    String searchDomain(DomainSpec spec, String query, TurnRetrievalCache cache,
                        String conversationId, String userId, AgentCheckpointLink link,
                        KbTestTraceCollector collector) {
        if (spec == null) {
            return "未知领域，无法检索";
        }
        TurnRetrievalCache store = cache == null ? new TurnRetrievalCache() : cache;
        String key = TurnRetrievalCache.key(conversationId, query, spec.name(), spec.name());
        String cached = store.get(key);
        if (cached != null) {
            return cached;
        }
        RagQueryRewrite rewritten = rewrite(userId, conversationId, query);
        List<String> queries = rewritten.queries() == null ? List.of() : rewritten.queries();
        collectTraces(collector, spec, queries, query);
        RetrievalResult result;
        try {
            if (queries.size() <= 1) {
                String single = queries.isEmpty() ? query : queries.get(0);
                result = retrievalService.retrieve(single, ragProperties.getTopK(),
                        ragProperties.getEffectiveMinReliableScore(), spec.name());
            } else {
                int rank = PgVectorScoreNormalizer.DEFAULT_RANK_CONSTANT;
                result = retrievalService.retrieveMany(queries, List.of(spec.name()), ragProperties.getTopK(),
                        ragProperties.getEffectiveMinReliableScore(), rank, task -> kbIngestScheduler.schedule(task));
            }
        } catch (RuntimeException ex) {
            record(link, spec, query, rewritten, 0, ex.toString(), AgentCheckpointLink.KIND_RAG);
            throw ex;
        }
        record(link, spec, query, rewritten, hitCount(result), null, AgentCheckpointLink.KIND_RAG);
        String text = format(result);
        if (rewritten.relational() && queryRewriteOrchestrator != null) {
            String hint = queryRewriteOrchestrator.relationHint();
            if (StringUtils.hasText(hint)) {
                text = hint.trim() + "\n\n" + text;
            }
        }
        store.put(key, text);
        return text;
    }

    private String runSubAgent(DomainSpec spec, String query, int parentRemaining, TurnRetrievalCache cache,
                               String conversationId, String userId, AgentCheckpointLink link,
                               KbTestTraceCollector collector) {
        if (spec == null || mainRoute(spec.llmConfigId())) {
            record(link, spec, query, null, null, "领域模型不可用", AgentCheckpointLink.KIND_SUB_AGENT);
            return "领域模型不可用";
        }
        LlmConfigRecord cfg;
        ChatModel model;
        try {
            cfg = llmClientFactory.getConfig(spec.llmConfigId());
            model = llmClientFactory.getChatModel(spec.llmConfigId());
        } catch (RuntimeException ex) {
            log.warn("domain model unavailable domain={}: {}", spec.name(), ex.toString());
            record(link, spec, query, null, null, ex.toString(), AgentCheckpointLink.KIND_SUB_AGENT);
            return "领域模型不可用";
        }
        InputBudgetCalculator.InputBudget budget;
        try {
            budget = inputBudgetCalculator.calculate(cfg);
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? "领域模型预算不可用" : ex.getMessage();
            record(link, spec, query, null, null, message, AgentCheckpointLink.KIND_SUB_AGENT);
            return message;
        }
        ToolCallback search = domainCallback(spec, cache, conversationId, userId, link, collector);
        TokenCounter counter = TokenCounterRegistry.forConfig(spec.llmConfigId());
        int schemaTokens = counter.countTools(List.of(search.getToolDefinition()));
        int outputCap = Math.min(budget.outputMaxTokens(), Math.max(1, parentRemaining));
        CallBudget callBudget = new CallBudget(budget.inputBudget(), schemaTokens, outputCap);
        String system = systemFor(spec);
        boolean linked = link != null && StringUtils.hasText(link.childThreadName());
        BaseCheckpointSaver saver = linked ? checkpointSaverFactory.getSaver() : new MemorySaver();
        String threadId = linked ? link.childThreadName() : "kb-" + spec.name() + "-" + UUID.randomUUID();
        ReactAgent agent = ReactAgent.builder()
                .name("kb-" + spec.name())
                .model(model)
                .systemPrompt(system)
                .saver(saver)
                .stateSerializer(CheckpointSaverFactory.graphSerializer())
                .tools(search)
                .hooks(modelLimit(), toolLimit())
                .interceptors(subInterceptors(spec.llmConfigId(), schemaTokens, query))
                .build();
        RunnableConfig.Builder configBuilder = RunnableConfig.builder()
                .threadId(threadId)
                .addMetadata(CallBudget.META_KEY, callBudget)
                .addMetadata(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT, new SystemPromptHolder(system));
        if (collector != null) {
            configBuilder.addMetadata(KbTestTraceCollector.META_KEY, collector);
        }
        RunnableConfig config = configBuilder.build();
        String failure = null;
        try {
            AssistantMessage reply = CheckpointGraphCaller.invoke(
                    agent, subAgentMessages(query), query, config);
            String text = reply == null || reply.getText() == null ? "" : reply.getText().trim();
            return text.isEmpty() ? "未检索到可引用内容" : text;
        } catch (Exception ex) {
            failure = ex.toString();
            log.warn("domain agent failed domain={}: {}", spec.name(), ex.toString());
            return "领域检索失败";
        } finally {
            if (linked) {
                record(link, spec, query, null, null, failure, AgentCheckpointLink.KIND_SUB_AGENT);
                ChildCheckpointRelease.releaseQuietly(saver, config);
            }
        }
    }

    private ToolCallback domainCallback(DomainSpec spec, TurnRetrievalCache cache,
                                       String conversationId, String userId, AgentCheckpointLink link,
                                       KbTestTraceCollector collector) {
        BiFunction<SearchRequest, org.springframework.ai.chat.model.ToolContext, String> fn = (req, ctx) ->
                searchDomain(
                        spec,
                        req == null || req.query() == null ? "" : req.query(),
                        cache,
                        conversationId,
                        userId,
                        link,
                        collector != null ? collector : collectorOf(ctx));
        return FunctionToolCallback.builder(SEARCH_TOOL, fn)
                .description("检索当前领域下的全部文档。只调用一次，query 使用用户原问题。")
                .inputType(SearchRequest.class)
                .build();
    }

    /**
     * 逐改写问句记录检索诊断轨迹。collector 为空（线上聊天）时不做任何事。
     */
    private void collectTraces(KbTestTraceCollector collector, DomainSpec spec, List<String> queries, String fallback) {
        if (collector == null || spec == null) {
            return;
        }
        List<String> asked = queries == null || queries.isEmpty() ? List.of(fallback) : queries;
        for (String askedQuery : asked) {
            if (askedQuery == null || askedQuery.isBlank()) {
                continue;
            }
            try {
                RetrievalTrace trace = retrievalService.diagnose(askedQuery, spec.name(), collector.options());
                collector.record(spec.name(), askedQuery, trace);
            } catch (RuntimeException ex) {
                collector.fail(spec.name(), ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
        }
    }

    private Interceptor[] subInterceptors(String configId, int schemaTokens, String query) {
        return new Interceptor[]{
                new WujiSystemPromptInterceptor(),
                new TokenBudgetInterceptor(configId, schemaTokens, enforcer),
                new DomainSearchCallCollapser(query, objectMapper)
        };
    }

    private ModelCallLimitHook modelLimit() {
        return ModelCallLimitHook.builder()
                .runLimit(Math.max(1, agentProperties.getMaxModelCalls()))
                .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
                .build();
    }

    private ToolCallLimitHook toolLimit() {
        return ToolCallLimitHook.builder()
                .runLimit(Math.max(1, agentProperties.getMaxToolRounds()))
                .exitBehavior(ToolCallLimitHook.ExitBehavior.ERROR)
                .build();
    }

    /**
     * 子 Agent 只收到这一次查询。主会话历史不进入该列表。
     */
    static List<Message> subAgentMessages(String query) {
        return List.of(new UserMessage(query == null ? "" : query));
    }

    private RagQueryRewrite rewrite(String userId, String conversationId, String query) {
        if (ragProperties.getQueryRewrite() == null || !ragProperties.getQueryRewrite().isEnabled()
                || queryRewriteOrchestrator == null) {
            return RagQueryRewrite.original(query);
        }
        RagQueryRewrite rewritten = queryRewriteOrchestrator.rewrite(userId, conversationId, query);
        if (rewritten == null || rewritten.queries() == null || rewritten.queries().isEmpty()) {
            return RagQueryRewrite.original(query);
        }
        return rewritten;
    }

    DomainSpec resolve(String domain) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        Map<String, DomainSpec> specs = domains();
        return specs.get(domain.trim());
    }

    private String domainCatalog() {
        Map<String, DomainSpec> specs = domains();
        if (specs.isEmpty()) {
            return "\n\n当前没有已配置领域。";
        }
        StringBuilder text = new StringBuilder("\n\n可选领域：");
        for (DomainSpec spec : specs.values()) {
            text.append("\n- ").append(spec.name());
            if (StringUtils.hasText(spec.description())) {
                text.append("：").append(spec.description().trim());
            }
        }
        return text.toString();
    }

    private Map<String, DomainSpec> domains() {
        Map<String, DomainSpec> specs = new LinkedHashMap<>();
        List<KbDomain> rows = domainRepository == null ? List.of() : domainRepository.listAll();
        if (rows == null) {
            rows = List.of();
        }
        for (KbDomain row : rows) {
            if (row == null || !StringUtils.hasText(row.name()) || !StringUtils.hasText(row.llmConfigId())) {
                continue;
            }
            if (mainRoute(row.llmConfigId())) {
                continue;
            }
            String description = StringUtils.hasText(row.description()) ? row.description().trim() : "";
            specs.put(row.name().trim(), new DomainSpec(row.name().trim(), description, row.llmConfigId().trim()));
        }
        return specs;
    }

    private static String format(RetrievalResult result) {
        if (result == null || result.rejected() || result.hits() == null || result.hits().isEmpty()) {
            if (result != null && StringUtils.hasText(result.rejectReason())) {
                return result.rejectReason();
            }
            return "无可靠知识命中";
        }
        StringBuilder text = new StringBuilder();
        for (RetrievalResult.Hit hit : result.hits()) {
            if (hit == null) {
                continue;
            }
            text.append('[').append(hit.score()).append("]\n");
            text.append(hit.content() == null ? "" : hit.content()).append("\n\n");
        }
        return text.toString().trim();
    }

    private static TurnRetrievalCache cacheOf(org.springframework.ai.chat.model.ToolContext ctx) {
        Object value = metadata(ctx).get(TurnRetrievalCache.META_KEY);
        return value instanceof TurnRetrievalCache cache ? cache : null;
    }

    private static CallBudget callBudgetOf(org.springframework.ai.chat.model.ToolContext ctx) {
        Object value = metadata(ctx).get(CallBudget.META_KEY);
        return value instanceof CallBudget budget ? budget : null;
    }

    private static String userOf(org.springframework.ai.chat.model.ToolContext ctx) {
        Object value = metadata(ctx).get(TokenBudgetEnforcer.USER_ID);
        return value instanceof String text ? text : "";
    }

    private static String conversationOf(org.springframework.ai.chat.model.ToolContext ctx) {
        Object value = metadata(ctx).get(TokenBudgetEnforcer.CONVERSATION_ID);
        return value instanceof String text ? text : "";
    }

    private static KbTestTraceCollector collectorOf(org.springframework.ai.chat.model.ToolContext ctx) {
        Object value = metadata(ctx).get(KbTestTraceCollector.META_KEY);
        return value instanceof KbTestTraceCollector collector ? collector : null;
    }

    /**
     * 链接在工具上下文上，不在父 {@link RunnableConfig#metadata()} 里。
     * {@code ToolCallRequest} 构造时会复制 metadata，拦截器只能改到那份副本；
     * {@code AgentToolNode} 再把副本放进 {@code ToolContext}。父 metadata 始终没有这一键。
     */
    static AgentCheckpointLink linkOf(org.springframework.ai.chat.model.ToolContext ctx) {
        AgentCheckpointLink link = linkFrom(ctx == null ? null : ctx.getContext());
        if (link == null) {
            link = linkFrom(metadata(ctx));
        }
        if (link == null) {
            return null;
        }
        if (StringUtils.hasText(link.parentThreadName())) {
            return link;
        }
        String parent = parentThread(ctx);
        if (!StringUtils.hasText(parent)) {
            return null;
        }
        return new AgentCheckpointLink(parent, link.parentToolCallId(), link.childThreadName());
    }

    private static AgentCheckpointLink linkFrom(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object value = map.get(AgentCheckpointLink.META_KEY);
        if (!(value instanceof AgentCheckpointLink link) || !StringUtils.hasText(link.childThreadName())) {
            return null;
        }
        return link;
    }

    private static String parentThread(org.springframework.ai.chat.model.ToolContext ctx) {
        if (ctx == null || ctx.getContext() == null) {
            return "";
        }
        Object config = ctx.getContext().get("_AGENT_CONFIG_");
        if (!(config instanceof RunnableConfig runnable)) {
            return "";
        }
        return runnable.threadId().orElse("");
    }

    private void record(AgentCheckpointLink link, DomainSpec spec, String query, RagQueryRewrite rewritten,
                        Integer hitCount, String error, String kind) {
        if (link == null || linkRepository == null) {
            return;
        }
        try {
            linkRepository.insert(new AgentCheckpointLinkWrite(
                    link.parentThreadName(),
                    link.parentToolCallId(),
                    link.childThreadName(),
                    kind,
                    spec == null ? null : spec.name(),
                    query,
                    rewrittenJson(rewritten),
                    hitCount,
                    error));
        } catch (RuntimeException ex) {
            log.warn("checkpoint link insert failed kind={}: {}", kind, ex.toString());
        }
    }

    private String rewrittenJson(RagQueryRewrite rewritten) {
        if (rewritten == null || rewritten.queries() == null || rewritten.queries().isEmpty() || objectMapper == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rewritten.queries());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return null;
        }
    }

    private static int hitCount(RetrievalResult result) {
        if (result == null || result.hits() == null) {
            return 0;
        }
        return result.hits().size();
    }

    private static Map<String, Object> metadata(org.springframework.ai.chat.model.ToolContext ctx) {
        if (ctx == null || ctx.getContext() == null) {
            return Map.of();
        }
        Object config = ctx.getContext().get("_AGENT_CONFIG_");
        if (config instanceof RunnableConfig runnable && runnable.metadata().isPresent()) {
            return runnable.metadata().get();
        }
        return ctx.getContext();
    }

    /**
     * 解析工具入参。测试和回放用，线上走 {@link Request}。
     */
    Request read(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null ? "{}" : json);
            return new Request(node.path("query").asText(""), node.path("domain").asText(""));
        } catch (Exception ex) {
            return new Request("", "");
        }
    }

    /**
     * 子 Agent 系统提示只取库表正文。检索范围是当前领域的全部文档。
     */
    String systemFor(DomainSpec spec) {
        return spec == null ? "" : spec.systemPrompt(activeDomainPrompt());
    }

    private String activeDomainPrompt() {
        String code = ragProperties == null ? "" : ragProperties.getDomainAgentPromptCode();
        if (promptTemplateService == null || !StringUtils.hasText(code)) {
            return DomainSpec.DEFAULT_SYSTEM;
        }
        String loaded = promptTemplateService.loadActiveContent(code);
        return StringUtils.hasText(loaded) ? loaded.trim() : DomainSpec.DEFAULT_SYSTEM;
    }

    private boolean mainRoute(String configId) {
        if (!StringUtils.hasText(configId) || modelProperties == null) {
            return false;
        }
        String id = configId.trim();
        if (StringUtils.hasText(modelProperties.getPrimaryConfigId())
                && id.equals(modelProperties.getPrimaryConfigId().trim())) {
            return true;
        }
        if (modelProperties.getFallbackConfigIds() == null) {
            return false;
        }
        for (String fallback : modelProperties.getFallbackConfigIds()) {
            if (StringUtils.hasText(fallback) && id.equals(fallback.trim())) {
                return true;
            }
        }
        return false;
    }

    public record Request(String query, String domain) {
    }

    public record SearchRequest(String query) {
    }

    record DomainSpec(String name, String description, String llmConfigId) {
        static final String DEFAULT_SYSTEM = "只根据本领域检索结果回答，不要编造。需要证据时只调用一次 search_domain，query 使用用户原问题。";

        String systemPrompt(String base) {
            return StringUtils.hasText(base) ? base.trim() : DEFAULT_SYSTEM;
        }
    }
}
