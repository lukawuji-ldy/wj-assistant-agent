package com.wuji.assistant.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.agent.scheduler.SafeTaskScheduler;
import com.wuji.assistant.agent.dto.ChatResult;
import com.wuji.assistant.agent.dto.ChatStreamRequest;
import com.wuji.assistant.agent.memory.LlmMemoryExtractOrchestrator;
import com.wuji.assistant.agent.memory.LlmSummaryOrchestrator;
import com.wuji.assistant.agent.model.LlmCallAuditor;
import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.model.SwallowedLlmErrors;
import com.wuji.assistant.agent.observability.AgentTelemetry;
import com.wuji.assistant.agent.observability.ChatMdc;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.prompt.WujiSystemPromptInterceptor;
import com.wuji.assistant.agent.stream.AgentStreamBridge;
import com.wuji.assistant.agent.checkpoint.AgentCheckpointLink;
import com.wuji.assistant.agent.stream.CheckpointGraphCaller;
import com.wuji.assistant.agent.stream.StreamSession;
import com.wuji.assistant.agent.stream.StreamSessionRegistry;
import com.wuji.assistant.agent.token.CallBudget;
import com.wuji.assistant.agent.token.CompressOnce;
import com.wuji.assistant.agent.token.ContextDraft;
import com.wuji.assistant.agent.token.ConversationTrimmer;
import com.wuji.assistant.agent.token.FirstModelWindow;
import com.wuji.assistant.agent.token.InputBudgetCalculator;
import com.wuji.assistant.agent.token.TokenBudgetEnforcer;
import com.wuji.assistant.agent.token.SystemPromptHolder;
import com.wuji.assistant.agent.token.TokenCounter;
import com.wuji.assistant.agent.token.TokenCounterRegistry;
import com.wuji.assistant.agent.token.TurnRetrievalCache;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.ShortTermContext;
import com.wuji.assistant.memory.ShortTermMemoryService;
import com.wuji.assistant.memory.extract.ExplicitRememberDetector;
import com.wuji.assistant.memory.extract.MemoryExtractService;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import com.wuji.assistant.memory.retrieve.LongTermMemoryRetriever;
import com.wuji.assistant.memory.retrieve.MemoryItem;
import com.wuji.assistant.memory.retrieve.MemoryRetrieveOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 聊天门面：短期记忆 + Prompt + ModelRouter + 有界 ReactAgent。
 *
 * @author liudy
 */
@Service
public class ChatFacade {

    private static final Logger log = LoggerFactory.getLogger(ChatFacade.class);

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ShortTermMemoryService shortTermMemoryService;
    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final AgentFactory agentFactory;
    private final LlmCallAuditor llmCallAuditor;
    private final WujiModelProperties modelProperties;
    private final WujiAgentProperties agentProperties;
    private final WujiMemoryProperties memoryProperties;
    private final WujiRagProperties ragProperties;
    private final MemoryExtractService memoryExtractService;
    private final LlmMemoryExtractOrchestrator memoryExtractOrchestrator;
    private final LlmSummaryOrchestrator summaryOrchestrator;
    private final LongTermMemoryRetriever longTermMemoryRetriever;
    private final StreamSessionRegistry streamSessionRegistry;
    private final ObjectMapper objectMapper;
    private final AgentTelemetry agentTelemetry;
    private final Scheduler chatTailScheduler;
    private final Scheduler llmCallScheduler;
    private final Scheduler dbScheduler;
    private final InputBudgetCalculator inputBudgetCalculator;
    private final ConversationTrimmer trimmer;

    private static final String MEMORY_NOTE =
            "（说明：句中「我」指当前登录用户，请依据上文「已知用户长期记忆」回答其偏好/画像，禁止当作询问助手自身。）\n";

    public ChatFacade(ConversationRepository conversationRepository,
                      ChatMessageRepository chatMessageRepository,
                      ShortTermMemoryService shortTermMemoryService,
                      PromptTemplateService promptTemplateService,
                      ModelRouter modelRouter,
                      AgentFactory agentFactory,
                      LlmCallAuditor llmCallAuditor,
                      WujiModelProperties modelProperties,
                      WujiAgentProperties agentProperties,
                      WujiMemoryProperties memoryProperties,
                      WujiRagProperties ragProperties,
                      MemoryExtractService memoryExtractService,
                      LlmMemoryExtractOrchestrator memoryExtractOrchestrator,
                      LlmSummaryOrchestrator summaryOrchestrator,
                      LongTermMemoryRetriever longTermMemoryRetriever,
                      StreamSessionRegistry streamSessionRegistry,
                      ObjectMapper objectMapper,
                      AgentTelemetry agentTelemetry,
                      @Qualifier("chatTailScheduler") Scheduler chatTailScheduler,
                      @Qualifier("llmCallScheduler") Scheduler llmCallScheduler,
                      @Qualifier("dbScheduler") Scheduler dbScheduler) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.shortTermMemoryService = shortTermMemoryService;
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.agentFactory = agentFactory;
        this.llmCallAuditor = llmCallAuditor;
        this.modelProperties = modelProperties;
        this.agentProperties = agentProperties;
        this.memoryProperties = memoryProperties;
        this.ragProperties = ragProperties;
        this.memoryExtractService = memoryExtractService;
        this.memoryExtractOrchestrator = memoryExtractOrchestrator;
        this.summaryOrchestrator = summaryOrchestrator;
        this.longTermMemoryRetriever = longTermMemoryRetriever;
        this.streamSessionRegistry = streamSessionRegistry;
        this.objectMapper = objectMapper;
        this.agentTelemetry = agentTelemetry;
        this.inputBudgetCalculator = new InputBudgetCalculator();
        this.trimmer = new ConversationTrimmer();
        this.chatTailScheduler = chatTailScheduler;
        this.llmCallScheduler = llmCallScheduler;
        this.dbScheduler = dbScheduler;
    }

    /**
     * 流式对话（支持 streamId 续传与心跳 ping）。
     *
     * @param userId  来自 JWT 的用户
     * @param request 请求体
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> stream(String userId, ChatStreamRequest request) {
        if (request != null && StringUtils.hasText(request.streamId())) {
            return resumeStream(userId, request);
        }
        if (request == null || !StringUtils.hasText(request.message())) {
            return Flux.just(emitStandaloneError(ErrorCode.BAD_REQUEST.getCode(), "message 不能为空", false));
        }
        return Mono.fromCallable(() -> prepare(userId, request, "STREAMING"))
                .subscribeOn(llmCallScheduler)
                .flatMapMany(this::runStream)
                .onErrorResume(ex -> {
                    Throwable mapped = mapAgentError(ex);
                    String errCode = mapped instanceof WujiException w ? w.getErrorCode().getCode()
                            : ErrorCode.INTERNAL_ERROR.getCode();
                    String msg = mapped.getMessage() == null ? ErrorCode.INTERNAL_ERROR.getMessage() : mapped.getMessage();
                    return Flux.just(emitStandaloneError(errCode, msg, true));
                });
    }

    /**
     * 非流式对话：在 {@code llmCallScheduler} 上拿到完整回复。取消订阅不打断工作线程。
     *
     * @param userId  来自 JWT 的用户
     * @param request 请求体（与流式相同）
     * @return 完整结果
     */
    public Mono<ChatResult> chat(String userId, ChatStreamRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            return Mono.error(new WujiException(ErrorCode.BAD_REQUEST, "message 不能为空"));
        }
        // 禁止 subscribeOn：cancel 会 interrupt，JDK HttpClient 报 Request was interrupted
        return DetachedBlockingMono.fromCallable(llmCallScheduler, () -> doChat(userId, request));
    }

    private Flux<ServerSentEvent<String>> resumeStream(String userId, ChatStreamRequest request) {
        return streamSessionRegistry.findActive(request.streamId(), userId)
                .<Flux<ServerSentEvent<String>>>map(session ->
                        Flux.fromIterable(session.replayAfter(request.lastEventId())))
                .orElseGet(() -> Flux.just(emitStandaloneError(
                        ErrorCode.STREAM_EXPIRED.getCode(),
                        ErrorCode.STREAM_EXPIRED.getMessage(),
                        false)));
    }

    private ChatResult doChat(String userId, ChatStreamRequest request) {
        StreamContext ctx = prepare(userId, request, "STREAMING");
        ChatMdc.put(ctx.traceId(), ctx.conversation().getConversationId(), ctx.userId(), agentProperties.getId());
        try {
            return agentTelemetry.observe("chat.request", new String[]{
                    "conversationId", ctx.conversation().getConversationId(),
                    "userId", ctx.userId(),
                    "biz.traceId", ctx.traceId()
            }, () -> doChatInternal(ctx));
        } finally {
            ChatMdc.clear();
        }
    }

    private ChatResult doChatInternal(StreamContext ctx) {
        try {
            return finishChat(ctx);
        } catch (Exception ex) {
            if (ctx.repackForContextLength(ex)) {
                log.warn("context length exceeded, retry once messageId={}",
                        ctx.assistantMsg().getMessageId());
                try {
                    return finishChat(ctx);
                } catch (Exception retryEx) {
                    ex = retryEx;
                }
            }
            // RestClient/Reactor 在取消时会置 interrupt；先清掉以便落库并返回 JSON，避免前端收到纯文本 Internal Server Error
            Thread.interrupted();
            chatMessageRepository.updateContentAndStatus(
                    ctx.assistantMsg().getMessageId(), "", "CANCELLED");
            Throwable mapped = mapAgentError(ex);
            if (mapped instanceof WujiException wuji) {
                throw wuji;
            }
            String msg = mapped.getMessage() == null ? ErrorCode.INTERNAL_ERROR.getMessage() : mapped.getMessage();
            throw new WujiException(ErrorCode.INTERNAL_ERROR, msg, mapped);
        }
    }

    private ChatResult finishChat(StreamContext ctx) {
        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                ctx.traceId(),
                ctx.conversation().getConversationId(),
                ctx.assistantMsg().getMessageId(),
                ctx.userId(),
                "CHAT",
                null,
                ctx.systemPrompt(),
                ctx.userPrompt(),
                ctx::systemPrompt,
                ctx::claimEstimate
        );
        RunnableConfig runnableConfig = runnableConfig(ctx);
        ModelRouter.RoutedResult<String> routed = modelRouter.execute(audit, client -> {
            try {
                ReactAgent agent = agentFactory.getOrCreate(client.configId());
                if (!client.configId().equals(ctx.configId())) {
                    ctx.repack(client.config());
                }
                AssistantMessage reply = CheckpointGraphCaller.invoke(
                        agent, ctx.messages(), ctx.userPrompt(), runnableConfig);
                String text = reply == null || reply.getText() == null ? "" : reply.getText();
                RuntimeException swallowed = SwallowedLlmErrors.toException(text);
                if (swallowed != null) {
                    throw swallowed;
                }
                return text;
            } catch (Exception ex) {
                throw AgentFactory.mapLimitException(ex);
            }
        });
        String full = routed.value() == null ? "" : routed.value();
        chatMessageRepository.updateContentAndStatus(
                ctx.assistantMsg().getMessageId(), full, "COMPLETED", routed.configId());
        triggerExtract(ctx, full);
        triggerCompress(ctx);
        return new ChatResult(
                ctx.conversation().getConversationId(),
                ctx.assistantMsg().getMessageId(),
                ctx.userMsg().getMessageId(),
                ctx.traceId(),
                full,
                routed.configId(),
                ctx.truncationInfo().isEmpty() ? null : ctx.truncationInfo()
        );
    }

    private StreamContext prepare(String userId, ChatStreamRequest request, String assistantStatus) {
        ModelRouter.IndexedClient opened = modelRouter.openFrom(0).orElseThrow(() ->
                new WujiException(ErrorCode.MODEL_UNAVAILABLE, "无可用 LLM 配置"));
        LlmConfigRecord config = opened.client().config();

        Conversation conversation;
        if (StringUtils.hasText(request.conversationId())) {
            conversation = conversationRepository.requireOwned(userId, request.conversationId());
        } else {
            conversation = conversationRepository.create(userId, null);
        }

        int maxMessages = memoryProperties.getShort().getMaxMessageCount();
        ShortTermContext shortTerm = shortTermMemoryService.loadContext(
                userId, conversation.getConversationId(), maxMessages);
        List<ChatMessage> history = shortTerm.messages();

        ChatMessage userMsg = chatMessageRepository.insert(
                conversation.getConversationId(), userId, "user", request.message(), "COMPLETED");
        conversationRepository.bumpMessageCount(conversation.getConversationId(), 1);

        if (memoryProperties.getExtract() != null
                && memoryProperties.getExtract().isExplicitDetectEnabled()
                && ExplicitRememberDetector.matches(request.message())) {
            try {
                memoryExtractService.rememberExplicit(
                        conversation.getConversationId(), userMsg.getMessageId(), userId, request.message());
            } catch (Exception e) {
                // L1 失败不阻断对话
            }
        }

        ChatMessage assistantPlaceholder = chatMessageRepository.insert(
                conversation.getConversationId(), userId, "assistant", "", assistantStatus);
        conversationRepository.bumpMessageCount(conversation.getConversationId(), 1);

        Mono<PromptParts> promptMono = Mono.fromCallable(() -> loadPromptParts(request.message()))
                .subscribeOn(dbScheduler);
        Mono<List<MemoryItem>> memoryMono = Mono.fromCallable(() -> loadMemoryItems(userId, request.message()))
                .subscribeOn(dbScheduler);
        var loaded = Mono.zip(promptMono, memoryMono).block();
        if (loaded == null) {
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "上下文装载失败");
        }
        PromptParts prompts = loaded.getT1();
        List<MemoryItem> memoryItems = loaded.getT2() == null ? List.of() : loaded.getT2();
        UserParts userParts = splitUser(prompts.userPrompt(), request.message());
        if (!memoryItems.isEmpty()) {
            userParts = new UserParts(MEMORY_NOTE + userParts.prefix(), userParts.raw(), userParts.suffix());
            log.info("memory inject into system, userId={}, items={}", userId, memoryItems.size());
        }

        ContextDraft draft = new ContextDraft(
                prompts.systemPrompt(),
                userParts.prefix(),
                userParts.raw(),
                userParts.suffix(),
                shortTerm.summaryJson(),
                memoryItems,
                toDialogue(history));
        ConversationTrimmer.TrimmedRequest rendered = trimmer.assemble(draft);

        String streamId = IdGenerator.nextBizId("s_");
        String traceId = IdGenerator.nextBizId("t_");
        StreamSession session = streamSessionRegistry.register(streamId, userId);

        return new StreamContext(userId, conversation, userMsg, assistantPlaceholder,
                streamId, traceId, session, config, draft, rendered, new CompressOnce(), new FirstModelWindow());
    }

    private Flux<ServerSentEvent<String>> runStream(StreamContext ctx) {
        ChatMdc.put(ctx.traceId(), ctx.conversation().getConversationId(), ctx.userId(), agentProperties.getId());
        AtomicReference<StringBuilder> contentBuf = new AtomicReference<>(new StringBuilder());
        long start = System.currentTimeMillis();
        StreamSession session = ctx.session();

        Map<String, Object> metaData = new LinkedHashMap<>();
        metaData.put("conversationId", ctx.conversation().getConversationId());
        metaData.put("messageId", ctx.assistantMsg().getMessageId());
        metaData.put("userMessageId", ctx.userMsg().getMessageId());
        metaData.put("traceId", ctx.traceId());
        metaData.put("streamId", ctx.streamId());
        ServerSentEvent<String> meta = emit(session, "meta", metaData);

        Flux<ServerSentEvent<String>> llmPart = streamWithFailover(ctx, 0, contentBuf, start, session);
        Flux<ServerSentEvent<String>> main = Flux.concat(Flux.just(meta), llmPart);
        return withHeartbeat(main, agentProperties.getStream().getHeartbeatInterval(),
                        () -> emit(session, "ping", Map.of("ts", System.currentTimeMillis())))
                .doOnComplete(session::touch)
                .doFinally(sig -> ChatMdc.clear());
    }

    /**
     * 心跳只旁路订阅。{@code main.then()} 会把冷流再订一次，模型会跑两遍且流不结束。
     */
    static Flux<ServerSentEvent<String>> withHeartbeat(Flux<ServerSentEvent<String>> main,
                                                       Duration heartbeat,
                                                       Supplier<ServerSentEvent<String>> ping) {
        if (heartbeat == null || heartbeat.isZero() || heartbeat.isNegative()) {
            return main;
        }
        return Flux.defer(() -> {
            Sinks.One<Boolean> stop = Sinks.one();
            Flux<ServerSentEvent<String>> pings = Flux.interval(heartbeat)
                    .map(tick -> ping.get())
                    .takeUntilOther(stop.asMono());
            return main.doFinally(sig -> stop.tryEmitValue(Boolean.TRUE)).mergeWith(pings);
        });
    }

    private Flux<ServerSentEvent<String>> streamWithFailover(StreamContext ctx,
                                                             int fromIndex,
                                                             AtomicReference<StringBuilder> contentBuf,
                                                             long start,
                                                             StreamSession session) {
        Optional<ModelRouter.IndexedClient> opened = modelRouter.openFrom(fromIndex);
        if (opened.isEmpty()) {
            return Mono.fromCallable(() -> {
                chatMessageRepository.updateContentAndStatus(
                        ctx.assistantMsg().getMessageId(), contentBuf.get().toString(), "CANCELLED");
                return emit(session, "error", Map.of(
                        "code", ErrorCode.MODEL_UNAVAILABLE.getCode(),
                        "message", "无可用 LLM 配置",
                        "retryable", true,
                        "streamId", ctx.streamId()
                ));
            }).subscribeOn(chatTailScheduler).flux();
        }
        return attemptStream(ctx, opened.get(), 1, contentBuf, start, session);
    }

    private Flux<ServerSentEvent<String>> attemptStream(StreamContext ctx,
                                                        ModelRouter.IndexedClient indexed,
                                                        int attempt,
                                                        AtomicReference<StringBuilder> contentBuf,
                                                        long start,
                                                        StreamSession session) {
        ModelRouter.RoutedClient routed = indexed.client();
        AtomicBoolean deltaSent = new AtomicBoolean(false);
        int maxAttempts = Math.max(1, modelProperties.getRetry().getMaxAttempts());
        Duration backoff = modelProperties.getRetry().getBackoff() == null
                ? Duration.ofSeconds(1) : modelProperties.getRetry().getBackoff();

        ReactAgent agent = agentFactory.getOrCreate(routed.configId());
        if (!routed.configId().equals(ctx.configId())) {
            ctx.repack(routed.config());
        }
        RunnableConfig runnableConfig = runnableConfig(ctx);

        Flux<ServerSentEvent<String>> deltas;
        try {
            deltas = AgentStreamBridge.toContentDeltas(CheckpointGraphCaller.stream(
                            agent, ctx.messages(), ctx.userPrompt(), runnableConfig))
                    .map(chunk -> {
                        deltaSent.set(true);
                        contentBuf.get().append(chunk);
                        return emit(session, "delta", Map.of("content", chunk));
                    });
        } catch (Exception ex) {
            return handleStreamError(ctx, indexed, attempt, maxAttempts, backoff,
                    contentBuf, start, session, false, mapAgentError(ex));
        }

        Flux<ServerSentEvent<String>> tail = Mono.fromCallable(() -> {
                    String full = contentBuf.get().toString();
                    chatMessageRepository.updateContentAndStatus(
                            ctx.assistantMsg().getMessageId(), full, "COMPLETED", routed.configId());
                    triggerExtract(ctx, full);
                    triggerCompress(ctx);
                    long latency = System.currentTimeMillis() - start;
                    llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                            ctx.traceId(),
                            ctx.conversation().getConversationId(),
                            ctx.assistantMsg().getMessageId(),
                            ctx.userId(),
                            "CHAT",
                            null,
                            routed.configId(),
                            routed.config().getProvider(),
                            attempt,
                            routed.fallback(),
                            "SUCCESS",
                            null,
                            (int) latency,
                            null,
                            null,
                            Map.of(
                                    "system", ctx.systemPrompt(),
                                    "user", ctx.userPrompt(),
                                    "model", routed.config().getModel()
                            ),
                            Map.of("content", full),
                            ctx.claimEstimate()
                    ));
                    Map<String, Object> done = new LinkedHashMap<>();
                    done.put("finishReason", "stop");
                    done.put("modelId", routed.configId());
                    Map<String, Object> truncation = ctx.truncationInfo();
                    if (truncation != null && Boolean.TRUE.equals(truncation.get("truncated"))) {
                        done.put("truncation_info", truncation);
                    }
                    return emit(session, "done", done);
                })
                .subscribeOn(chatTailScheduler)
                .flux();

        return Flux.concat(deltas, tail)
                .doOnCancel(() -> persistOnDisconnect(ctx, contentBuf, session, routed.configId()))
                .onErrorResume(ex -> handleStreamError(ctx, indexed, attempt, maxAttempts, backoff,
                        contentBuf, start, session, deltaSent.get(), mapAgentError(ex)));
    }

    /**
     * 客户端/代理断开时仍落库并写入终态 done，便于续传拿到终端事件。
     */
    private void persistOnDisconnect(StreamContext ctx,
                                     AtomicReference<StringBuilder> contentBuf,
                                     StreamSession session,
                                     String modelConfigId) {
        SafeTaskScheduler.submit(chatTailScheduler, () -> {
            try {
                String full = contentBuf.get().toString();
                if (full == null) {
                    full = "";
                }
                chatMessageRepository.updateContentAndStatus(
                        ctx.assistantMsg().getMessageId(), full,
                        full.isBlank() ? "CANCELLED" : "COMPLETED",
                        modelConfigId);
                boolean hasTerminal = session.replayAfter(0L).stream()
                        .anyMatch(e -> "done".equals(e.event()) || "error".equals(e.event()));
                if (!hasTerminal) {
                    emit(session, "done", Map.of(
                            "finishReason", "disconnect",
                            "modelId", modelConfigId == null ? "" : modelConfigId
                    ));
                }
            } catch (Exception ignored) {
            }
        }, "disconnect:" + ctx.streamId(), Duration.ofMinutes(5));
    }

    private Flux<ServerSentEvent<String>> handleStreamError(StreamContext ctx,
                                                           ModelRouter.IndexedClient indexed,
                                                           int attempt,
                                                           int maxAttempts,
                                                           Duration backoff,
                                                           AtomicReference<StringBuilder> contentBuf,
                                                           long start,
                                                           StreamSession session,
                                                           boolean deltaSent,
                                                           Throwable ex) {
        ModelRouter.RoutedClient routed = indexed.client();
        ErrorCode mapped = ex instanceof WujiException w ? w.getErrorCode() : modelRouter.mapErrorCode(ex);
        String errCode = mapped.getCode();
        String msg = ex.getMessage() == null ? mapped.getMessage() : ex.getMessage();
        boolean retryable = mapped != ErrorCode.AGENT_MAX_ITERATIONS && modelRouter.isRetryable(ex);
        log.error("chat stream failed messageId={} code={} deltaSent={} attempt={}: {}",
                ctx.assistantMsg().getMessageId(), errCode, deltaSent, attempt, msg, ex);

        Mono<Void> auditFail = Mono.fromRunnable(() -> llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                ctx.traceId(),
                ctx.conversation().getConversationId(),
                ctx.assistantMsg().getMessageId(),
                ctx.userId(),
                "CHAT",
                null,
                routed.configId(),
                routed.config().getProvider(),
                attempt,
                routed.fallback(),
                "FAILED",
                errCode,
                (int) (System.currentTimeMillis() - start),
                null,
                null,
                Map.of("system", ctx.systemPrompt(), "user", ctx.userPrompt()),
                Map.of("error", msg),
                ctx.claimEstimate()
        ))).subscribeOn(chatTailScheduler).then();

        if (!deltaSent && ctx.repackForContextLength(ex)) {
            log.warn("context length exceeded, retry once messageId={}",
                    ctx.assistantMsg().getMessageId());
            return auditFail.thenMany(attemptStream(ctx, indexed, attempt, contentBuf, start, session));
        }

        if (!deltaSent && mapped != ErrorCode.AGENT_MAX_ITERATIONS && modelRouter.isRetryable(ex)) {
            if (attempt < maxAttempts) {
                Duration delay = backoff.isZero() ? Duration.ZERO : backoff.multipliedBy(attempt);
                return auditFail.thenMany(
                        Mono.delay(delay)
                                .thenMany(attemptStream(ctx, indexed, attempt + 1, contentBuf, start, session)));
            }
            if (modelRouter.isFailoverWorthy(ex)) {
                contentBuf.set(new StringBuilder());
                return auditFail.thenMany(
                        streamWithFailover(ctx, indexed.index() + 1, contentBuf, start, session));
            }
        }

        boolean finalRetryable = retryable;
        return auditFail.thenMany(Mono.fromCallable(() -> {
            chatMessageRepository.updateContentAndStatus(
                    ctx.assistantMsg().getMessageId(),
                    contentBuf.get().toString(),
                    "CANCELLED",
                    routed.configId());
            return emit(session, "error", Map.of(
                    "code", errCode,
                    "message", msg,
                    "retryable", finalRetryable,
                    "streamId", ctx.streamId()
            ));
        }).subscribeOn(chatTailScheduler).flux());
    }

    private void triggerExtract(StreamContext ctx, String assistantText) {
        if (memoryProperties.getExtract() == null || !memoryProperties.getExtract().isEnabled()) {
            return;
        }
        Runnable task = () -> memoryExtractOrchestrator.extractAsync(
                ctx.conversation().getConversationId(),
                ctx.assistantMsg().getMessageId(),
                ctx.userId(),
                ctx.userPrompt(),
                assistantText);
        if (memoryProperties.getExtract().isAsync()) {
            SafeTaskScheduler.submit(llmCallScheduler, task,
                    "extract:" + ctx.assistantMsg().getMessageId(),
                    memoryProperties.getExtract().getTimeout());
        } else {
            task.run();
        }
    }

    private void triggerCompress(StreamContext ctx) {
        int tokenThreshold = memoryProperties.getShort().getCompressTokenThreshold();
        Runnable task = () -> summaryOrchestrator.compress(
                ctx.userId(), ctx.conversation().getConversationId(), tokenThreshold);
        SafeTaskScheduler.submit(llmCallScheduler, task,
                "compress:" + ctx.conversation().getConversationId(),
                Duration.ofMinutes(5));
    }

    private static UserParts splitUser(String rendered, String rawMessage) {
        String raw = rawMessage == null ? "" : rawMessage;
        String prompt = rendered == null ? "" : rendered;
        if (!raw.isEmpty()) {
            int at = prompt.indexOf(raw);
            if (at >= 0) {
                return new UserParts(prompt.substring(0, at), raw, prompt.substring(at + raw.length()));
            }
        }
        return new UserParts(prompt, raw.isEmpty() ? prompt : "", "");
    }

    private PromptParts loadPromptParts(String message) {
        String systemPrompt = promptTemplateService.loadAndRender(
                agentProperties.getSystemPromptCode(),
                Map.of(),
                "你是企业智能助手，回答需准确。");
        String ragAnswerPrompt = promptTemplateService.loadAndRender(
                ragProperties.getAnswerPromptCode(),
                Map.of(),
                "");
        if (StringUtils.hasText(ragAnswerPrompt)) {
            systemPrompt = systemPrompt + "\n\n" + ragAnswerPrompt.trim();
        }
        String userPrompt = promptTemplateService.loadAndRender(
                agentProperties.getUserPromptCode(),
                Map.of("message", message == null ? "" : message),
                message == null ? "" : message);
        return new PromptParts(systemPrompt, userPrompt);
    }

    private List<MemoryItem> loadMemoryItems(String userId, String message) {
        WujiMemoryProperties.Retrieve retrieveCfg = memoryProperties.getRetrieve() == null
                ? new WujiMemoryProperties.Retrieve()
                : memoryProperties.getRetrieve();
        MemoryRetrieveOptions retrieveOptions = new MemoryRetrieveOptions(
                retrieveCfg.getTopK(),
                retrieveCfg.getWeightSimilarity(),
                retrieveCfg.getWeightConfidence(),
                retrieveCfg.getWeightFreshness(),
                retrieveCfg.getWeightImportance(),
                retrieveCfg.isSemanticEnabled(),
                retrieveCfg.getSemanticTopK(),
                retrieveCfg.getSemanticMinScore());
        List<MemoryItem> items = longTermMemoryRetriever.retrieveItems(userId, message, retrieveOptions);
        return items == null ? List.of() : items;
    }

    private static List<Message> toDialogue(List<ChatMessage> history) {
        List<Message> messages = new ArrayList<>();
        if (history == null) {
            return messages;
        }
        for (ChatMessage m : history) {
            if (m == null || m.getRole() == null) {
                continue;
            }
            if ("user".equalsIgnoreCase(m.getRole())) {
                messages.add(new UserMessage(m.getContent() == null ? "" : m.getContent()));
            } else if ("assistant".equalsIgnoreCase(m.getRole())) {
                messages.add(new AssistantMessage(m.getContent() == null ? "" : m.getContent()));
            }
        }
        return messages;
    }

    static boolean isContextLengthExceeded(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("context_length_exceeded")
                        || lower.contains("context_length")
                        || lower.contains("maximum context length")
                        || lower.contains("context length")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private RunnableConfig runnableConfig(StreamContext ctx) {
        return RunnableConfig.builder()
                .threadId(ctx.userId() + ":" + ctx.conversation().getConversationId())
                .addMetadata(AgentCheckpointLink.PARENT_THREAD_KEY,
                        ctx.userId() + ":" + ctx.conversation().getConversationId())
                .addMetadata(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT, ctx.promptHolder())
                .addMetadata(CallBudget.META_KEY, ctx.callBudget())
                .addMetadata(TurnRetrievalCache.META_KEY, ctx.retrievalCache())
                .addMetadata(CompressOnce.META_KEY, ctx.compressOnce())
                .addMetadata(FirstModelWindow.META_KEY, ctx.firstWindow())
                .addMetadata(TokenBudgetEnforcer.USER_ID, ctx.userId())
                .addMetadata(TokenBudgetEnforcer.CONVERSATION_ID, ctx.conversation().getConversationId())
                .addMetadata(TokenBudgetEnforcer.USER_MESSAGE_ID, ctx.userMsg().getMessageId())
                .addMetadata(TokenBudgetEnforcer.ASSISTANT_MESSAGE_ID, ctx.assistantMsg().getMessageId())
                .build();
    }

    private static Throwable mapAgentError(Throwable ex) {
        return AgentFactory.mapLimitException(ex);
    }

    private ServerSentEvent<String> emit(StreamSession session, String event, Map<String, Object> data) {
        return session.append(event, toJson(data));
    }

    private ServerSentEvent<String> emitStandaloneError(String code, String message, boolean retryable) {
        return ServerSentEvent.<String>builder()
                .id("1")
                .event("error")
                .data(toJson(Map.of(
                        "code", code,
                        "message", message,
                        "retryable", retryable
                )))
                .build();
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialize_failed\"}";
        }
    }

    private record PromptParts(String systemPrompt, String userPrompt) {
    }

    private record UserParts(String prefix, String raw, String suffix) {
    }

    private final class StreamContext {
        private final String userId;
        private final Conversation conversation;
        private final ChatMessage userMsg;
        private final ChatMessage assistantMsg;
        private final String streamId;
        private final String traceId;
        private final StreamSession session;
        private LlmConfigRecord config;
        private ContextDraft draft;
        private String systemPrompt;
        private String userPrompt;
        private List<Message> messages;
        private final SystemPromptHolder promptHolder = new SystemPromptHolder();
        private final TurnRetrievalCache retrievalCache = new TurnRetrievalCache();
        private final CompressOnce compressOnce;
        private final FirstModelWindow firstWindow;
        private CallBudget callBudget;
        private final AtomicBoolean estimateClaimed = new AtomicBoolean(false);
        private boolean contextLengthRetried;

        private StreamContext(String userId,
                              Conversation conversation,
                              ChatMessage userMsg,
                              ChatMessage assistantMsg,
                              String streamId,
                              String traceId,
                              StreamSession session,
                              LlmConfigRecord config,
                              ContextDraft draft,
                              ConversationTrimmer.TrimmedRequest packed,
                              CompressOnce compressOnce,
                              FirstModelWindow firstWindow) {
            this.userId = userId;
            this.conversation = conversation;
            this.userMsg = userMsg;
            this.assistantMsg = assistantMsg;
            this.streamId = streamId;
            this.traceId = traceId;
            this.session = session;
            this.config = config;
            this.draft = draft;
            this.compressOnce = compressOnce == null ? new CompressOnce() : compressOnce;
            this.firstWindow = firstWindow == null ? new FirstModelWindow() : firstWindow;
            apply(packed);
        }

        private void apply(ConversationTrimmer.TrimmedRequest packed) {
            this.systemPrompt = packed.systemPrompt();
            this.promptHolder.set(packed.systemPrompt());
            this.userPrompt = packed.userPrompt();
            this.messages = packed.messages();
            InputBudgetCalculator.InputBudget budget = inputBudgetCalculator.calculate(config);
            int tools = 0;
            if (StringUtils.hasText(configId())) {
                try {
                    tools = agentFactory.toolSchemaTokens(configId());
                } catch (RuntimeException ex) {
                    log.warn("tool schema tokens unavailable configId={}: {}", configId(), ex.toString());
                }
            }
            if (callBudget == null) {
                callBudget = new CallBudget(budget.inputBudget(), tools, 0);
            } else {
                callBudget.reset(budget.inputBudget(), tools, 0);
            }
        }

        private void repack(LlmConfigRecord next) {
            this.config = next;
            if (!firstWindow.initialMessages().isEmpty()) {
                apply(new ConversationTrimmer.TrimmedRequest(
                        firstWindow.initialSystem(), userPrompt, firstWindow.initialMessages(), null, 0, 0, false));
                return;
            }
            apply(trimmer.assemble(draft));
        }

        private String configId() {
            return config == null || config.getConfigId() == null ? "" : config.getConfigId();
        }

        private Integer claimEstimate() {
            int tokens = firstWindow.estimatedPromptTokens();
            if (tokens <= 0) {
                return null;
            }
            return estimateClaimed.compareAndSet(false, true) ? tokens : null;
        }

        private String userId() {
            return userId;
        }

        private Conversation conversation() {
            return conversation;
        }

        private ChatMessage userMsg() {
            return userMsg;
        }

        private ChatMessage assistantMsg() {
            return assistantMsg;
        }

        private List<Message> messages() {
            return messages;
        }

        private String streamId() {
            return streamId;
        }

        private String traceId() {
            return traceId;
        }

        private String systemPrompt() {
            String held = promptHolder.get();
            return StringUtils.hasText(held) ? held : systemPrompt;
        }

        private String userPrompt() {
            return userPrompt;
        }

        private StreamSession session() {
            return session;
        }

        private Map<String, Object> truncationInfo() {
            Map<String, Object> info = firstWindow.truncation();
            return info == null ? Map.of() : info;
        }

        private FirstModelWindow firstWindow() {
            return firstWindow;
        }

        private SystemPromptHolder promptHolder() {
            return promptHolder;
        }

        private CallBudget callBudget() {
            return callBudget;
        }

        private TurnRetrievalCache retrievalCache() {
            return retrievalCache;
        }

        private CompressOnce compressOnce() {
            return compressOnce;
        }

        /**
         * 尚未发出 token 且模型报上下文超限时，再用同一套丢弃至少再丢掉一轮历史，只试一次。
         *
         * @param error 模型异常
         * @return true 表示可以再调一次
         */
        private boolean repackForContextLength(Throwable error) {
            if (contextLengthRetried || !isContextLengthExceeded(error)) {
                return false;
            }
            contextLengthRetried = true;
            List<Message> basis = firstWindow.initialMessages().isEmpty() ? messages : firstWindow.initialMessages();
            String system = StringUtils.hasText(firstWindow.initialSystem()) ? firstWindow.initialSystem() : systemPrompt();
            InputBudgetCalculator.InputBudget budget = inputBudgetCalculator.calculate(config);
            int tools = callBudget == null ? 0 : callBudget.toolSchemaTokens();
            TokenCounter counter = TokenCounterRegistry.forConfig(configId());
            ConversationTrimmer.TrimmedRequest tightened = trimmer.trimConversationDroppingAtLeastOne(
                    system, basis, budget.inputBudget(), tools, counter);
            if (tightened.droppedUserMessages() <= 0) {
                return false;
            }
            apply(tightened);
            return true;
        }
    }
}
