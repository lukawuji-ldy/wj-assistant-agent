package com.wuji.assistant.agent.kbtest;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.Interceptor;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLink;
import com.wuji.assistant.agent.checkpoint.CheckpointSaverFactory;
import com.wuji.assistant.agent.checkpoint.CheckpointThreadReader;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.prompt.WujiSystemPromptInterceptor;
import com.wuji.assistant.agent.stream.CheckpointGraphCaller;
import com.wuji.assistant.agent.token.CallBudget;
import com.wuji.assistant.agent.token.InputBudgetCalculator;
import com.wuji.assistant.agent.token.SystemPromptHolder;
import com.wuji.assistant.agent.token.TokenBudgetEnforcer;
import com.wuji.assistant.agent.token.TokenBudgetInterceptor;
import com.wuji.assistant.agent.token.TokenCounter;
import com.wuji.assistant.agent.token.TokenCounterRegistry;
import com.wuji.assistant.agent.token.TurnRetrievalCache;
import com.wuji.assistant.agent.tool.DomainKnowledgeTool;
import com.wuji.assistant.agent.tool.KnowledgeSearchLinkInterceptor;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.KnowledgeRetrievalService;
import com.wuji.assistant.rag.diagnose.RetrievalTestOptions;
import com.wuji.assistant.rag.diagnose.RetrievalTrace;
import com.wuji.assistant.rag.router.KbDomain;
import com.wuji.assistant.rag.router.KbDomainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 管理台「知识库测试」核心执行器。
 * <p>
 * 直接检索模式委托 {@link KnowledgeRetrievalService#diagnose}；
 * Agent 模式构建只挂 {@code knowledge_search} 的专用主 Agent，主线程名 {@code kb-test:{uuid}}，
 * 领域 subAgent 沿用 {@link DomainKnowledgeTool} 的领域目录、改写、预算与 checkpoint 链接能力。
 * <p>
 * 不复用带 MCP 的 {@code AgentFactory} 实例。问题正文与检索片段不写应用日志。
 *
 * @author liudy
 */
@Service
public class KnowledgeAgentTestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAgentTestService.class);

    /** 专用测试主线程名前缀。管理台 checkpoint scope=test 按此匹配。 */
    public static final String TEST_THREAD_PREFIX = "kb-test:";

    static final String PROMPT_CODE = "rag.kb_test_agent.system";
    static final String TOOL_DESCRIPTION = "选择与问题相关的一个或多个领域检索知识库，只返回结论。";
    static final String DEFAULT_SYSTEM = """
            你是知识库测试助手。只根据领域检索结果回答，不要用模型常识补写内部知识。
            先判断问题涉及多少个领域，可同时选择多个相关领域并逐个调用 knowledge_search。
            每个领域只调用一次，query 使用用户原问题；基于返回证据汇总，不要编造。
            """;
    static final String SYNTHETIC_USER_ID = "kb-test";

    private final KnowledgeRetrievalService retrievalService;
    private final KbDomainRepository domainRepository;
    private final LlmClientFactory llmClientFactory;
    private final WujiAgentProperties agentProperties;
    private final WujiModelProperties modelProperties;
    private final WujiRagProperties ragProperties;
    private final CheckpointSaverFactory checkpointSaverFactory;
    private final TokenBudgetEnforcer enforcer;
    private final InputBudgetCalculator inputBudgetCalculator;
    private final PromptTemplateService promptTemplateService;
    private final DomainKnowledgeTool domainKnowledgeTool;
    private final CheckpointThreadReader threadReader;

    public KnowledgeAgentTestService(KnowledgeRetrievalService retrievalService,
                                     KbDomainRepository domainRepository,
                                     LlmClientFactory llmClientFactory,
                                     WujiAgentProperties agentProperties,
                                     WujiModelProperties modelProperties,
                                     WujiRagProperties ragProperties,
                                     CheckpointSaverFactory checkpointSaverFactory,
                                     TokenBudgetEnforcer enforcer,
                                     InputBudgetCalculator inputBudgetCalculator,
                                     PromptTemplateService promptTemplateService,
                                     DomainKnowledgeTool domainKnowledgeTool,
                                     CheckpointThreadReader threadReader) {
        this.retrievalService = retrievalService;
        this.domainRepository = domainRepository;
        this.llmClientFactory = llmClientFactory;
        this.agentProperties = agentProperties;
        this.modelProperties = modelProperties;
        this.ragProperties = ragProperties;
        this.checkpointSaverFactory = checkpointSaverFactory;
        this.enforcer = enforcer;
        this.inputBudgetCalculator = inputBudgetCalculator;
        this.promptTemplateService = promptTemplateService;
        this.domainKnowledgeTool = domainKnowledgeTool;
        this.threadReader = threadReader;
    }

    /**
     * 直接检索诊断。domainName 必填且必须存在。
     *
     * @param query      检索问句
     * @param domainName 领域名（知识库命名空间）
     * @param options    临时参数覆盖；{@code null} 用线上默认
     * @return 阈值过滤前的双路原始结果与 RRF 融合明细
     */
    public RetrievalTrace diagnoseRetrieval(String query, String domainName, RetrievalTestOptions options) {
        if (!StringUtils.hasText(query)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "query 不能为空");
        }
        String domain = requireDomain(domainName);
        return retrievalService.diagnose(query.trim(), domain, resolveOptions(options));
    }

    /**
     * Agent 模式：专用主 Agent 自动识别一或多个领域并汇总。
     *
     * @param query   用户问题
     * @param options 临时参数覆盖；{@code null} 用线上默认
     * @return 状态、答案、领域轨迹与主线程信息
     */
    public KnowledgeAgentTestResult runAgent(String query, RetrievalTestOptions options) {
        String question = query == null ? "" : query.trim();
        RetrievalTestOptions effective = resolveOptions(options);
        KbTestTraceCollector collector = new KbTestTraceCollector(effective);
        List<String> warnings = new ArrayList<>();
        String threadName = TEST_THREAD_PREFIX + UUID.randomUUID();
        if (!StringUtils.hasText(question)) {
            warnings.add("query 为空");
            return new KnowledgeAgentTestResult(KbTestStatus.FAILED, null, threadName, null,
                    List.of(), List.of(), warnings);
        }
        ReactAgent agent;
        RunnableConfig config;
        try {
            String configId = primaryConfigId();
            agent = buildAgent(configId, collector);
            config = agentConfig(threadName, configId, collector);
        } catch (RuntimeException ex) {
            log.warn("kb-test agent unavailable: {}", ex.toString());
            warnings.add("主 Agent 不可用：" + safeMessage(ex));
            return failed(threadName, null, collector, warnings);
        }
        String answer;
        try {
            AssistantMessage reply = CheckpointGraphCaller.invoke(agent, mainMessages(question), question, config);
            answer = reply == null || reply.getText() == null ? "" : reply.getText().trim();
        } catch (Exception ex) {
            log.warn("kb-test agent failed thread={}: {}", threadName, ex.toString());
            warnings.add("主 Agent 执行失败：" + safeMessage(ex));
            return failed(threadName, collector.domains(), collector, warnings);
        }
        String parentThreadId = resolveThreadId(threadName);
        List<String> collected = new ArrayList<>(warnings);
        collected.addAll(collector.failures());
        if (parentThreadId == null) {
            collected.add("checkpoint 未落库，轨迹不完整");
        }
        KbTestStatus status = collector.failures().isEmpty() ? KbTestStatus.SUCCEEDED : KbTestStatus.PARTIAL;
        return new KnowledgeAgentTestResult(
                status, answer, threadName, parentThreadId,
                collector.domains(), collector.domainTraces(), List.copyOf(collected));
    }

    private KnowledgeAgentTestResult failed(String threadName, List<String> domains,
                                            KbTestTraceCollector collector, List<String> warnings) {
        List<String> collected = new ArrayList<>(warnings);
        collected.addAll(collector.failures());
        return new KnowledgeAgentTestResult(KbTestStatus.FAILED, null, threadName, resolveThreadId(threadName),
                domains == null ? List.of() : domains, collector.domainTraces(), List.copyOf(collected));
    }

    /**
     * 专用主 Agent：只挂 {@code knowledge_search}，使用 primary CHAT 配置。
     */
    private ReactAgent buildAgent(String configId, KbTestTraceCollector collector) {
        ToolCallback tool = domainKnowledgeTool.callback(TOOL_DESCRIPTION);
        TokenCounter counter = TokenCounterRegistry.forConfig(configId);
        int schemaTokens = counter.countTools(List.of(tool.getToolDefinition()));
        Interceptor[] interceptors = new Interceptor[]{
                new WujiSystemPromptInterceptor(),
                new TokenBudgetInterceptor(configId, schemaTokens, enforcer),
                new KnowledgeSearchLinkInterceptor()
        };
        return ReactAgent.builder()
                .name("kb-test-main")
                .model(llmClientFactory.getChatModel(configId))
                .saver(checkpointSaverFactory.getSaver())
                .stateSerializer(CheckpointSaverFactory.graphSerializer())
                .tools(tool)
                .hooks(modelLimit(), toolLimit())
                .interceptors(interceptors)
                .build();
    }

    private RunnableConfig agentConfig(String threadName, String configId, KbTestTraceCollector collector) {
        String system = systemPrompt();
        CallBudget callBudget = callBudget(configId);
        return RunnableConfig.builder()
                .threadId(threadName)
                .addMetadata(AgentCheckpointLink.PARENT_THREAD_KEY, threadName)
                .addMetadata(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT, new SystemPromptHolder(system))
                .addMetadata(CallBudget.META_KEY, callBudget)
                .addMetadata(TurnRetrievalCache.META_KEY, new TurnRetrievalCache())
                .addMetadata(KbTestTraceCollector.META_KEY, collector)
                .addMetadata(TokenBudgetEnforcer.USER_ID, SYNTHETIC_USER_ID)
                .addMetadata(TokenBudgetEnforcer.CONVERSATION_ID, threadName)
                .build();
    }

    private CallBudget callBudget(String configId) {
        try {
            LlmConfigRecord cfg = llmClientFactory.getConfig(configId);
            InputBudgetCalculator.InputBudget budget = inputBudgetCalculator.calculate(cfg);
            TokenCounter counter = TokenCounterRegistry.forConfig(configId);
            ToolCallback tool = domainKnowledgeTool.callback(TOOL_DESCRIPTION);
            int schemaTokens = counter.countTools(List.of(tool.getToolDefinition()));
            return new CallBudget(budget.inputBudget(), schemaTokens, 0);
        } catch (RuntimeException ex) {
            log.warn("kb-test call budget unavailable: {}", ex.toString());
            return new CallBudget(0, 0, 0);
        }
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

    private String primaryConfigId() {
        String id = modelProperties == null ? null : modelProperties.getPrimaryConfigId();
        if (!StringUtils.hasText(id)) {
            throw new WujiException(ErrorCode.MODEL_UNAVAILABLE, "未配置主模型 llm_config.config_id");
        }
        return id.trim();
    }

    private String systemPrompt() {
        String loaded = promptTemplateService == null ? "" : promptTemplateService.loadActiveContent(PROMPT_CODE);
        return StringUtils.hasText(loaded) ? loaded.trim() : DEFAULT_SYSTEM;
    }

    /**
     * 临时参数覆盖：未显式给的 finalTopK / minReliableScore 回退线上配置，其余用后端默认。
     */
    private RetrievalTestOptions resolveOptions(RetrievalTestOptions override) {
        RetrievalTestOptions base = override == null ? RetrievalTestOptions.defaults() : override.validate();
        Integer finalTopK = base.finalTopK();
        Double minReliable = base.minReliableScore();
        return new RetrievalTestOptions(
                base.vectorTopK(),
                base.keywordTopK(),
                finalTopK != null ? finalTopK : Math.max(1, ragProperties.getTopK()),
                minReliable != null ? minReliable : ragProperties.getEffectiveMinReliableScore(),
                base.rrfRankConstant());
    }

    private String requireDomain(String domainName) {
        if (!StringUtils.hasText(domainName)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "domainName 不能为空");
        }
        String name = domainName.trim();
        List<KbDomain> domains = domainRepository == null ? List.of() : domainRepository.listAll();
        if (domains != null) {
            for (KbDomain row : domains) {
                if (row != null && StringUtils.hasText(row.name()) && name.equals(row.name().trim())) {
                    return name;
                }
            }
        }
        throw new WujiException(ErrorCode.NOT_FOUND, "领域不存在: " + name);
    }

    private String resolveThreadId(String threadName) {
        if (threadReader == null) {
            return null;
        }
        return threadReader.findThreadIdByName(threadName).orElse(null);
    }

    private static List<Message> mainMessages(String question) {
        return List.of(new UserMessage(question));
    }

    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return StringUtils.hasText(message) ? message : ex.getClass().getSimpleName();
    }
}
