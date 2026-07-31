package com.wuji.assistant.server.chat;

import com.wuji.assistant.agent.ChatFacade;
import com.wuji.assistant.agent.dto.ChatResult;
import com.wuji.assistant.agent.dto.ChatStreamRequest;
import com.wuji.assistant.common.api.ApiResponse;
import com.wuji.assistant.server.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 聊天接口（流式 / 非流式）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";

    private final ChatFacade chatFacade;

    public ChatController(ChatFacade chatFacade) {
        this.chatFacade = chatFacade;
    }

    /**
     * SSE 流式对话；支持 Last-Event-ID / 请求体 streamId 续传。
     *
     * @param request     请求体
     * @param lastEventId 可选 SSE 头
     * @return SSE
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestBody(required = false) ChatStreamRequest request,
            @RequestHeader(value = LAST_EVENT_ID_HEADER, required = false) String lastEventId) {
        ChatStreamRequest effective = mergeLastEventId(request, lastEventId);
        return CurrentUser.require()
                .flatMapMany(user -> chatFacade.stream(user.userId(), effective));
    }

    /**
     * 非流式对话；返回完整助手回复。
     *
     * @param request 请求体（与 stream 相同）
     * @return 统一包装的 ChatResult
     */
    @PostMapping
    public Mono<ApiResponse<ChatResult>> chat(@RequestBody ChatStreamRequest request) {
        return CurrentUser.require()
                .flatMap(user -> chatFacade.chat(user.userId(), request))
                .map(ApiResponse::ok);
    }

    private static ChatStreamRequest mergeLastEventId(ChatStreamRequest request, String lastEventIdHeader) {
        if (request == null) {
            Long headerId = parseLong(lastEventIdHeader);
            return new ChatStreamRequest(null, null, null, null, null, headerId);
        }
        if (request.lastEventId() != null || !StringUtils.hasText(lastEventIdHeader)) {
            return request;
        }
        return new ChatStreamRequest(
                request.conversationId(),
                request.message(),
                request.agentId(),
                request.collection(),
                request.streamId(),
                parseLong(lastEventIdHeader)
        );
    }

    private static Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
