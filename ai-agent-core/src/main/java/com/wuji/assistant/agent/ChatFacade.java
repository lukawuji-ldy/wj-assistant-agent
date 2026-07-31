package com.wuji.assistant.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.agent.config.WujiModelProperties;
import com.wuji.assistant.agent.dto.ChatResult;
import com.wuji.assistant.agent.dto.ChatStreamRequest;
import com.wuji.assistant.agent.model.LlmCallAuditor;
import com.wuji.assistant.agent.model.LlmClientFactory;
import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.agent.prompt.PromptTemplateService;
import com.wuji.assistant.agent.stream.StreamSession;
import com.wuji.assistant.agent.stream.StreamSessionRegistry;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.common.util.IdGenerator;
import com.wuji.assistant.memory.ShortTermMemoryService;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天门面：短期记忆 + Prompt + ChatClient（流式含心跳/续传 / 非流式）。
 *
 * @author liudy
 */
@Service
public class ChatFacade {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ShortTermMemoryService shortTermMemoryService;
    private final PromptTemplateService promptTemplateService;
    private final LlmClientFactory llmClientFactory;
    private final LlmCallAuditor llmCallAuditor;
    private final WujiModelProperties modelProperties;
    private final WujiAgentProperties agentProperties;
    private final StreamSessionRegistry streamSessionRegistry;
    private final ObjectMapper objectMapper;

    public ChatFacade(ConversationRepository conversationRepository,
                      ChatMessageRepository chatMessageRepository,
                      ShortTermMemoryService shortTermMemoryService,
                      PromptTemplateService promptTemplateService,
                      LlmClientFactory llmClientFactory,
                      LlmCallAuditor llmCallAuditor,
                      WujiModelProperties modelProperties,
                      WujiAgentProperties agentProperties,
                      StreamSessionRegistry streamSessionRegistry,
                      ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.shortTermMemoryService = shortTermMemoryService;
        this.promptTemplateService = promptTemplateService;
        this.llmClientFactory = llmClientFactory;
        this.llmCallAuditor = llmCallAuditor;
        this.modelProperties = modelProperties;
        this.agentProperties = agentProperties;
        this.streamSessionRegistry = streamSessionRegistry;
        this.objectMapper = objectMapper;
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
                    String errCode = ex instanceof WujiException w ? w.getErrorCode().getCode()
                            : ErrorCode.INTERNAL_ERROR.getCode();
                    String msg = ex.getMessage() == null ? ErrorCode.INTERNAL_ERROR.getMessage() : ex.getMessage();
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
        long start = System.currentTimeMillis();
        try {
            String content = ctx.chatClient().prompt()
                    .messages(ctx.messages())
                    .call()
                    .content();
            String full = content == null ? "" : content;
            chatMessageRepository.updateContentAndStatus(
                    ctx.assistantMsg().getMessageId(), full, "COMPLETED");
            long latency = System.currentTimeMillis() - start;
            llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                    ctx.traceId(),
                    ctx.conversation().getConversationId(),
                    ctx.assistantMsg().getMessageId(),
                    ctx.userId(),
                    ctx.config().getConfigId(),
                    ctx.config().getProvider(),
                    1,
                    false,
                    "SUCCESS",
                    null,
                    (int) latency,
                    null,
                    null,
                    Map.of(
                            "system", ctx.systemPrompt(),
                            "user", ctx.userPrompt(),
                            "model", ctx.config().getModel()
                    ),
                    Map.of("content", full)
            ));
            return new ChatResult(
                    ctx.conversation().getConversationId(),
                    ctx.assistantMsg().getMessageId(),
                    ctx.userMsg().getMessageId(),
                    ctx.traceId(),
                    full,
                    ctx.config().getConfigId()
            );
        } catch (Exception ex) {
            String errCode = ex instanceof WujiException w ? w.getErrorCode().getCode()
                    : ErrorCode.INTERNAL_ERROR.getCode();
            String msg = ex.getMessage() == null ? ErrorCode.INTERNAL_ERROR.getMessage() : ex.getMessage();
            chatMessageRepository.updateContentAndStatus(
                    ctx.assistantMsg().getMessageId(), "", "CANCELLED");
            llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                    ctx.traceId(),
                    ctx.conversation().getConversationId(),
                    ctx.assistantMsg().getMessageId(),
                    ctx.userId(),
                    ctx.config().getConfigId(),
                    ctx.config().getProvider(),
                    1,
                    false,
                    "FAILED",
                    errCode,
                    (int) (System.currentTimeMillis() - start),
                    null,
                    null,
                    Map.of("system", ctx.systemPrompt(), "user", ctx.userPrompt()),
                    Map.of("error", msg)
            ));
            if (ex instanceof WujiException wuji) {
                throw wuji;
            }
            throw new WujiException(ErrorCode.INTERNAL_ERROR, msg, ex);
        }
    }

    private StreamContext prepare(String userId, ChatStreamRequest request, String assistantStatus) {
        String configId = modelProperties.getPrimaryConfigId();
        ChatClient chatClient = llmClientFactory.getChatClient(configId);
        LlmConfigRecord cfg = llmClientFactory.getConfig(configId);

        Conversation conversation;
        if (StringUtils.hasText(request.conversationId())) {
            conversation = conversationRepository.requireOwned(userId, request.conversationId());
        } else {
            conversation = conversationRepository.create(userId, null);
        }

        List<ChatMessage> history = shortTermMemoryService.loadRecentMessages(
                userId, conversation.getConversationId(), agentProperties.getMemoryWindowSize());

        ChatMessage userMsg = chatMessageRepository.insert(
                conversation.getConversationId(), userId, "user", request.message(), "COMPLETED");
        conversationRepository.bumpMessageCount(conversation.getConversationId(), 1);

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

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
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
                messages, chatClient, cfg, streamId, traceId, systemPrompt, userPrompt, session);
    }

    private Flux<ServerSentEvent<String>> runStream(StreamContext ctx) {
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

        Flux<ServerSentEvent<String>> deltas = ctx.chatClient().prompt()
                .messages(ctx.messages())
                .stream()
                .content()
                .map(chunk -> {
                    contentBuf.get().append(chunk);
                    return emit(session, "delta", Map.of("content", chunk));
                });

        Flux<ServerSentEvent<String>> tail = Mono.fromCallable(() -> {
                    String full = contentBuf.get().toString();
                    chatMessageRepository.updateContentAndStatus(
                            ctx.assistantMsg().getMessageId(), full, "COMPLETED");
                    long latency = System.currentTimeMillis() - start;
                    llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                            ctx.traceId(),
                            ctx.conversation().getConversationId(),
                            ctx.assistantMsg().getMessageId(),
                            ctx.userId(),
                            ctx.config().getConfigId(),
                            ctx.config().getProvider(),
                            1,
                            false,
                            "SUCCESS",
                            null,
                            (int) latency,
                            null,
                            null,
                            Map.of(
                                    "system", ctx.systemPrompt(),
                                    "user", ctx.userPrompt(),
                                    "model", ctx.config().getModel()
                            ),
                            Map.of("content", full)
                    ));
                    return emit(session, "done", Map.of(
                            "finishReason", "stop",
                            "modelId", ctx.config().getConfigId()
                    ));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flux();

        Flux<ServerSentEvent<String>> main = Flux.concat(Flux.just(meta), deltas, tail)
                .onErrorResume(ex -> Mono.fromCallable(() -> {
                    String errCode = ex instanceof WujiException w ? w.getErrorCode().getCode()
                            : ErrorCode.INTERNAL_ERROR.getCode();
                    String msg = ex.getMessage() == null ? ErrorCode.INTERNAL_ERROR.getMessage() : ex.getMessage();
                    chatMessageRepository.updateContentAndStatus(
                            ctx.assistantMsg().getMessageId(),
                            contentBuf.get().toString(),
                            "CANCELLED");
                    llmCallAuditor.record(new LlmCallAuditor.AuditParams(
                            ctx.traceId(),
                            ctx.conversation().getConversationId(),
                            ctx.assistantMsg().getMessageId(),
                            ctx.userId(),
                            ctx.config().getConfigId(),
                            ctx.config().getProvider(),
                            1,
                            false,
                            "FAILED",
                            errCode,
                            (int) (System.currentTimeMillis() - start),
                            null,
                            null,
                            Map.of("system", ctx.systemPrompt(), "user", ctx.userPrompt()),
                            Map.of("error", msg)
                    ));
                    return emit(session, "error", Map.of(
                            "code", errCode,
                            "message", msg,
                            "retryable", true,
                            "streamId", ctx.streamId()
                    ));
                }).subscribeOn(Schedulers.boundedElastic()).flux());

        Duration heartbeat = agentProperties.getStream().getHeartbeatInterval();
        if (heartbeat == null || heartbeat.isZero() || heartbeat.isNegative()) {
            return main.doOnComplete(session::touch);
        }

        Flux<ServerSentEvent<String>> pings = Flux.interval(heartbeat)
                .map(tick -> emit(session, "ping", Map.of("ts", System.currentTimeMillis())))
                .takeUntilOther(main.then());

        return Flux.merge(main, pings).doOnComplete(session::touch);
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
            ChatClient chatClient,
            LlmConfigRecord config,
            String streamId,
            String traceId,
            String systemPrompt,
            String userPrompt,
            StreamSession session
    ) {
    }
}
