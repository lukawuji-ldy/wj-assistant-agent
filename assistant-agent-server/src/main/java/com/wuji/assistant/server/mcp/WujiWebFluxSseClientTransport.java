package com.wuji.assistant.server.mcp;

import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.util.function.Function;

/**
 * 加固版 WebFlux SSE Transport：容忍 SSE 重连后重复的 {@code endpoint} 事件，
 * 并为 inbound 订阅挂上 error consumer，避免 {@code onErrorDropped}。
 * <p>
 * 上游 {@link WebFluxSseClientTransport} 在 {@code Sinks.One} 已有值时再次收到
 * {@code endpoint} 会抛 {@code Failed to handle SSE endpoint event}。
 *
 * @author liudy
 */
public class WujiWebFluxSseClientTransport extends WebFluxSseClientTransport {

    private static final Logger log = LoggerFactory.getLogger(WujiWebFluxSseClientTransport.class);

    private static final String MESSAGE_EVENT_TYPE = "message";

    private static final String ENDPOINT_EVENT_TYPE = "endpoint";

    private Disposable inboundSubscription;

    /**
     * @param webClientBuilder WebClient
     * @param jsonMapper       JSON
     * @param sseEndpoint      SSE 路径，如 {@code /sse}
     */
    public WujiWebFluxSseClientTransport(WebClient.Builder webClientBuilder,
                                         McpJsonMapper jsonMapper,
                                         String sseEndpoint) {
        super(webClientBuilder, jsonMapper, sseEndpoint);
    }

    /**
     * 接受 endpoint URI：首次写入成功；若因重连重复写入（sink 已终止/溢出）则忽略并视为成功。
     *
     * @param sink endpoint sink
     * @param uri  服务端下发的 message endpoint
     * @return 是否可继续（无需中断 SSE 流）
     */
    static boolean acceptEndpointEvent(Sinks.One<String> sink, String uri) {
        Sinks.EmitResult result = sink.tryEmitValue(uri);
        if (result.isSuccess()) {
            return true;
        }
        // Sinks.One 首次成功后再次 emit 通常为 FAIL_TERMINATED（偶发 FAIL_OVERFLOW）
        if (result == Sinks.EmitResult.FAIL_TERMINATED || result == Sinks.EmitResult.FAIL_OVERFLOW) {
            log.debug("Ignoring duplicate MCP SSE endpoint event result={} uri={}", result, uri);
            return true;
        }
        log.warn("Failed to accept MCP SSE endpoint event result={} uri={}", result, uri);
        return false;
    }

    @Override
    public Mono<Void> connect(Function<Mono<JSONRPCMessage>, Mono<JSONRPCMessage>> handler) {
        Flux<ServerSentEvent<String>> events = eventStream();
        this.inboundSubscription = events.concatMap(event -> Mono.just(event).<JSONRPCMessage>handle((e, s) -> {
            if (ENDPOINT_EVENT_TYPE.equals(event.event())) {
                if (acceptEndpointEvent(messageEndpointSink, event.data())) {
                    s.complete();
                }
                else {
                    s.error(new RuntimeException("Failed to handle SSE endpoint event"));
                }
            }
            else if (MESSAGE_EVENT_TYPE.equals(event.event())) {
                try {
                    JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(this.jsonMapper, event.data());
                    s.next(message);
                }
                catch (IOException ioException) {
                    s.error(ioException);
                }
            }
            else {
                log.debug("Received unrecognized SSE event type: {}", event.event());
                s.complete();
            }
        }).transform(handler)).subscribe(
                null,
                err -> log.warn("MCP SSE inbound stream error: {}", err.toString())
        );
        return messageEndpointSink.asMono().then();
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            if (this.inboundSubscription != null && !this.inboundSubscription.isDisposed()) {
                this.inboundSubscription.dispose();
            }
        }).then(super.closeGracefully());
    }
}
