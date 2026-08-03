package com.wuji.assistant.server.chat;

import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.memory.model.ChatMessage;
import com.wuji.assistant.memory.model.Conversation;
import com.wuji.assistant.memory.repo.ChatMessageRepository;
import com.wuji.assistant.memory.repo.ConversationRepository;
import com.wuji.assistant.server.security.CurrentUser;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 会话与历史消息 API。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ConversationController(ConversationRepository conversationRepository,
                                  ChatMessageRepository chatMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * 新建会话。
     *
     * @param body 可选标题
     * @return 会话
     */
    @PostMapping
    public Mono<ApiResponse<Conversation>> create(@RequestBody(required = false) Map<String, String> body) {
        String title = body == null ? null : body.get("title");
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> ApiResponse.ok(conversationRepository.create(user.userId(), title)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 当前用户会话列表。
     *
     * @return 列表
     */
    @GetMapping
    public Mono<ApiResponse<List<Conversation>>> list() {
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> ApiResponse.ok(conversationRepository.listByUser(user.userId())))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 会话详情。
     *
     * @param conversationId 会话键
     * @return 会话
     */
    @GetMapping("/{conversationId}")
    public Mono<ApiResponse<Conversation>> get(@PathVariable String conversationId) {
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> ApiResponse.ok(
                                conversationRepository.requireOwned(user.userId(), conversationId)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 更新标题。
     *
     * @param conversationId 会话键
     * @param request        请求
     * @return 会话
     */
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
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 硬删除会话及其消息。
     *
     * @param conversationId 会话键
     * @return 空成功
     */
    @DeleteMapping("/{conversationId}")
    public Mono<ApiResponse<Void>> delete(@PathVariable String conversationId) {
        return CurrentUser.require().flatMap(user ->
                Mono.fromCallable(() -> {
                    conversationRepository.requireOwned(user.userId(), conversationId);
                    chatMessageRepository.deleteByConversation(conversationId, user.userId());
                    conversationRepository.deleteOwned(user.userId(), conversationId);
                    return ApiResponse.<Void>ok(null);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 历史消息。
     *
     * @param conversationId 会话键
     * @param limit          条数
     * @param offset         偏移
     * @return 消息列表
     */
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
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
