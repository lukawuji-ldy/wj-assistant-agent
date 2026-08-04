package com.wuji.assistant.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.dto.ChatResult;
import com.wuji.assistant.agent.dto.ChatStreamRequest;
import com.wuji.assistant.agent.memory.LlmMemoryExtractOrchestrator;
import com.wuji.assistant.agent.model.LlmCallAuditor;
import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.observability.AgentTelemetry;
import com.wuji.assistant.agent.observability.ChatMdc;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.prompt.WujiSystemPromptInterceptor;
import com.wuji.assistant.agent.stream.AgentStreamBridge;
import com.wuji.assistant.agent.stream.StreamSession;
import com.wuji.assistant.agent.stream.StreamSessionRegistry;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.ShortTermContext;
import com.wuji.assistant.memory.ShortTermMemoryService;
import com.wuji.assistant.memory.SummaryService;
import com.wuji.assistant.memory.extract.ExplicitRememberDetector;
import com.wuji.assistant.memory.extract.MemoryExtractService;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import com.wuji.assistant.memory.retrieve.LongTermMemoryRetriever;
import com.wuji.assistant.memory.retrieve.MemoryRetrieveOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private final MemoryExtractService memoryExtractService;
    private final LlmMemoryExtractOrchestrator memoryExtractOrchestrator;
    private final LongTermMemoryRetriever longTermMemoryRetriever;
    private final SummaryService summaryService;
    private final StreamSessionRegistry streamSessionRegistry;
    private final ObjectMapper objectMapper;
    private final AgentTelemetry agentTelemetry;

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
                      MemoryExtractService memoryExtractService,
                      LlmMemoryExtractOrchestrator memoryExtractOrchestrator,
                      LongTermMemoryRetriever longTermMemoryRetriever,
                      SummaryService summaryService,
                      StreamSessionRegistry streamSessionRegistry,
                      ObjectMapper objectMapper,
                      AgentTelemetry agentTelemetry) {
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
        this.memoryExtractService = memoryExtractService;
        this.memoryExtractOrchestrator = memoryExtractOrchestrator;
        this.longTermMemoryRetriever = longTermMemoryRetriever;
        this.summaryService = summaryService;
        this.streamSessionRegistry = streamSessionRegistry;
        this.objectMapper = objectMapper;
        this.agentTelemetry = agentTelemetry;
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
                .subscribeOn(Schedulers.boundedElastic())
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
     * 非流式对话：同步拿到完整回复后返回（阻塞调用在 boundedElastic 执行）。
     *
     * @param userId  来自 JWT 的用户
     * @param request 请求体（与流式相同）
     * @return 完整结果
     */
    public Mono<ChatResult> chat(String userId, ChatStreamRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            return Mono.error(new WujiException(ErrorCode.BAD_REQUEST, "message 不能为空"));
        }
        return Mono.fromCallable(() -> doChat(userId, request))
                .subscribeOn(Schedulers.boundedElastic());
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
        ModelRouter.AuditContext audit = new ModelRouter.AuditContext(
                ctx.traceId(),
                ctx.conversation().getConversationId(),
                ctx.assistantMsg().getMessageId(),
                ctx.userId(),
                ctx.systemPrompt(),
                ctx.userPrompt()
        );
        RunnableConfig runnableConfig = runnableConfig(ctx);
        try {
            ModelRouter.RoutedResult<String> routed = modelRouter.execute(audit, client -> {
                try {
                    ReactAgent agent = agentFactory.getOrCreate(client.configId());
                    AssistantMessage reply = agent.call(ctx.messages(), runnableConfig);
                    return reply == null || reply.getText() == null ? "" : reply.getText();
                } catch (Exception ex) {
                    throw AgentFactory.mapLimitException(ex);
                }
            });
            String full = routed.value() == null ? "" : routed.value();
            chatMessageRepository.updateContentAndStatus(
                    ctx.assistantMsg().getMessageId(), full, "COMPLETED");
            triggerExtract(ctx, full);
            triggerCompress(ctx);
            return new ChatResult(
                    ctx.conversation().getConversationId(),
                    ctx.assistantMsg().getMessageId(),
                    ctx.userMsg().getMessageId(),
                    ctx.traceId(),
                    full,
                    routed.configId()
            );
        } catch (Exception ex) {
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

    private StreamContext prepare(String userId, ChatStreamRequest request, String assistantStatus) {
        modelRouter.openFrom(0).orElseThrow(() ->
                new WujiException(ErrorCode.MODEL_UNAVAILABLE, "无可用 LLM 配置"));

        Conversation conversation;
        if (StringUtils.hasText(request.conversationId())) {
            conversation = conversationRepository.requireOwned(userId, request.conversationId());
        } else {
            conversation = conversationRepository.create(userId, null);
        }

        int maxMessages = memoryProperties.getShort() != null
                ? memoryProperties.getShort().getMaxMessageCount()
                : agentProperties.getMemoryWindowSize();
        int maxTokens = memoryProperties.getShort() != null
                ? memoryProperties.getShort().getMaxToken()
                : 8000;
        ShortTermContext shortTerm = shortTermMemoryService.loadContext(
                userId, conversation.getConversationId(), maxMessages, maxTokens);
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

        String systemPrompt = promptTemplateService.loadAndRender(
                agentProperties.getSystemPromptCode(),
                Map.of(),
                "你是企业智能助手，回答需准确。");
        String userPrompt = promptTemplateService.loadAndRender(
                agentProperties.getUserPromptCode(),
                Map.of("message", request.message()),
                request.message());

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
        String longTermBlock = longTermMemoryRetriever.retrieveBlock(
                userId, request.message(), retrieveOptions);
        // 合并进主 system（经 RunnableConfig metadata 注入，禁止再塞进 messages 以免 Append 累积）
        if (StringUtils.hasText(longTermBlock)) {
            systemPrompt = systemPrompt + "\n\n" + longTermBlock;
            userPrompt = "（说明：句中「我」指当前登录用户，请依据上文「已知用户长期记忆」回答其偏好/画像，禁止当作询问助手自身。）\n"
                    + userPrompt;
            log.info("memory inject into system, userId={}, chars={}", userId, longTermBlock.length());
        }
        if (StringUtils.hasText(shortTerm.summaryJson())) {
            systemPrompt = systemPrompt + "\n\n会话摘要（watermark 覆盖范围）:\n" + shortTerm.summaryJson();
        }

        // 仅 user/assistant 进入 ReactAgent messages；system 走 metadata → WujiSystemPromptInterceptor
        List<Message> messages = new ArrayList<>();
        for (ChatMessage m : history) {
            if ("user".equalsIgnoreCase(m.getRole())) {
                messages.add(new UserMessage(m.getContent()));
            } else if ("assistant".equalsIgnoreCase(m.getRole())) {
                messages.add(new AssistantMessage(m.getContent() == null ? "" : m.getContent()));
            }
        }
        messages.add(new UserMessage(userPrompt));

        String streamId = IdGenerator.nextBizId("s_");
        String traceId = IdGenerator.nextBizId("t_");
        StreamSession session = streamSessionRegistry.register(streamId, userId);

        return new StreamContext(userId, conversation, userMsg, assistantPlaceholder,
                messages, streamId, traceId, systemPrompt, userPrompt, session);
    }

    private Flux<ServerSentEvent<String>> runStream(StreamContext ctx) {
        ChatMdc.put(ctx.traceId(), ctx.conversation().getConversationId(), ctx.userId(), agentProperties.getId());
        AtomicReference<StringBuilder> contentBuf = new AtomicReference<>(new StringBuilder());
        long start = System.currentTimeMillis();
        StreamSession session = ctx.session();

        ServerSentEvent<String> meta = emit(session, "meta", Map.of(
                "conversationId", ctx.conversation().getConversationId(),
                "messageId", ctx.assistantMsg().getMessageId(),
                "userMessageId", ctx.userMsg().getMessageId(),
                "traceId", ctx.traceId(),
                "streamId", ctx.streamId()
        ));

        Flux<ServerSentEvent<String>> llmPart = streamWithFailover(ctx, 0, contentBuf, start, session);
        Flux<ServerSentEvent<String>> main = Flux.concat(Flux.just(meta), llmPart);

        Duration heartbeat = agentProperties.getStream().getHeartbeatInterval();
        Flux<ServerSentEvent<String>> result;
        if (heartbeat == null || heartbeat.isZero() || heartbeat.isNegative()) {
            result = main.doOnComplete(session::touch);
        } else {
            Flux<ServerSentEvent<String>> pings = Flux.interval(heartbeat)
                    .map(tick -> emit(session, "ping", Map.of("ts", System.currentTimeMillis())))
                    .takeUntilOther(main.then());
            result = Flux.merge(main, pings).doOnComplete(session::touch);
        }
        return result.doFinally(sig -> ChatMdc.clear());
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
            }).subscribeOn(Schedulers.boundedElastic()).flux();
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
        RunnableConfig runnableConfig = runnableConfig(ctx);

        Flux<ServerSentEvent<String>> deltas;
        try {
            deltas = AgentStreamBridge.toContentDeltas(agent.streamMessages(ctx.messages(), runnableConfig))
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
                            ctx.assistantMsg().getMessageId(), full, "COMPLETED");
                    triggerExtract(ctx, full);
                    triggerCompress(ctx);
                    long latency = System.currentTimeMillis() - start;
                    llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                            ctx.traceId(),
                            ctx.conversation().getConversationId(),
                            ctx.assistantMsg().getMessageId(),
                            ctx.userId(),
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
                            Map.of("content", full)
                    ));
                    return emit(session, "done", Map.of(
                            "finishReason", "stop",
                            "modelId", routed.configId()
                    ));
                })
                .subscribeOn(Schedulers.boundedElastic())
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
        Schedulers.boundedElastic().schedule(() -> {
            try {
                String full = contentBuf.get().toString();
                if (full == null) {
                    full = "";
                }
                chatMessageRepository.updateContentAndStatus(
                        ctx.assistantMsg().getMessageId(), full,
                        full.isBlank() ? "CANCELLED" : "COMPLETED");
                // 若缓冲尚无终端事件，补一条 done 供 resume 重放
                boolean hasTerminal = session.replayAfter(0L).stream()
                        .anyMatch(e -> "done".equals(e.event()) || "error".equals(e.event()));
                if (!hasTerminal) {
                    emit(session, "done", Map.of(
                            "finishReason", "disconnect",
                            "modelId", modelConfigId == null ? "" : modelConfigId
                    ));
                }
            } catch (Exception ignored) {
                // 断开路径不阻断
            }
        });
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
                Map.of("error", msg)
        ))).subscribeOn(Schedulers.boundedElastic()).then();

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
                    "CANCELLED");
            return emit(session, "error", Map.of(
                    "code", errCode,
                    "message", msg,
                    "retryable", finalRetryable,
                    "streamId", ctx.streamId()
            ));
        }).subscribeOn(Schedulers.boundedElastic()).flux());
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
            Schedulers.boundedElastic().schedule(task);
        } else {
            task.run();
        }
    }

    private void triggerCompress(StreamContext ctx) {
        if (memoryProperties.getShort() == null) {
            return;
        }
        int threshold = memoryProperties.getShort().getCompressMessageThreshold();
        int keep = memoryProperties.getShort().getKeepRecentMessages();
        Runnable task = () -> summaryService.compressIfNeeded(
                ctx.userId(), ctx.conversation().getConversationId(), threshold, keep);
        Schedulers.boundedElastic().schedule(task);
    }

    private static RunnableConfig runnableConfig(StreamContext ctx) {
        // Checkpoint thread = userId:conversationId；system 经 metadata 注入，避免 Append 累积 SystemMessage
        return RunnableConfig.builder()
                .threadId(ctx.userId() + ":" + ctx.conversation().getConversationId())
                .addMetadata(WujiSystemPromptInterceptor.META_SYSTEM_PROMPT, ctx.systemPrompt())
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

    private record StreamContext(
            String userId,
            Conversation conversation,
            ChatMessage userMsg,
            ChatMessage assistantMsg,
            List<Message> messages,
            String streamId,
            String traceId,
            String systemPrompt,
            String userPrompt,
            StreamSession session
    ) {
    }
}
