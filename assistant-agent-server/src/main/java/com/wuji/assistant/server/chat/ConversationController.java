package com.wuji.assistant.server.chat;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import com.wuji.assistant.server.security.CurrentUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final Scheduler dbScheduler;

    public ConversationController(ConversationRepository conversationRepository,
                                  ChatMessageRepository chatMessageRepository,
                                  @Qualifier("dbScheduler") Scheduler dbScheduler) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.dbScheduler = dbScheduler;
    }

    @PostMapping
    public Mono<ApiResponse<Conversation>> create(@RequestBody(required = false) Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> ApiResponse.ok(conversationRepository.create(user.userId(), title)))
                        .subscribeOn(dbScheduler));
    }

    @GetMapping
    public Mono<ApiResponse<List<Conversation>>> list() {
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> ApiResponse.ok(conversationRepository.listByUser(user.userId())))
                        .subscribeOn(dbScheduler));
    }

    @GetMapping("/{conversationId}")
    public Mono<ApiResponse<Conversation>> get(@PathVariable String conversationId) {
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> ApiResponse.ok(
                                conversationRepository.requireOwned(user.userId(), conversationId)))
                        .subscribeOn(dbScheduler));
    }

    @PatchMapping("/{conversationId}")
    public Mono<ApiResponse<Conversation>> patch(@PathVariable String conversationId,
                                                 @RequestBody UpdateConversationRequest request) {
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> {
                    if (request == null || !StringUtils.hasText(request.title())) {
                        return ApiResponse.<Conversation>fail("BAD_REQUEST", "title 不能为空");
                    }
                    return ApiResponse.ok(conversationRepository.updateTitle(
                            user.userId(), conversationId, request.title().trim()));
                }).subscribeOn(dbScheduler));
    }

    @DeleteMapping("/{conversationId}")
    public Mono<ApiResponse<Void>> delete(@PathVariable String conversationId) {
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> {
                    conversationRepository.requireOwned(user.userId(), conversationId);
                    chatMessageRepository.deleteByConversation(conversationId, user.userId());
                    conversationRepository.deleteOwned(user.userId(), conversationId);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(dbScheduler));
    }

    @GetMapping("/{conversationId}/messages")
    public Mono<ApiResponse<List<ChatMessage>>> messages(@PathVariable String conversationId,
                                                         @RequestParam(defaultValue = "50") int limit,
                                                         @RequestParam(defaultValue = "0") int offset) {
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> {
                    AuthUser u = user;
                    conversationRepository.requireOwned(u.userId(), conversationId);
                    int safeLimit = Math.min(Math.max(limit, 1), 200);
                    int safeOffset = Math.max(offset, 0);
                    return ApiResponse.ok(chatMessageRepository.listPageAsc(
                            conversationId, u.userId(), safeLimit, safeOffset));
                }).subscribeOn(dbScheduler));
    }
}
