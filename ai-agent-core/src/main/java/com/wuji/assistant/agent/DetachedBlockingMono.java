package com.wuji.assistant.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在独立线程跑阻塞调用，HTTP 取消时<strong>不</strong> interrupt 工作线程。
 * <p>
 * {@code subscribeOn(boundedElastic)} 在下游 cancel 时会打断线程；非流式 {@code POST /api/chat}
 * 在 LLM 完成前不写响应体，代理/前端超时会 cancel，进而把正在进行的 JDK HttpClient.get() 打断。
 *
 * @author liudy
 */
public final class DetachedBlockingMono {

    private static final Logger log = LoggerFactory.getLogger(DetachedBlockingMono.class);

    private static final AtomicInteger SEQ = new AtomicInteger();

    private DetachedBlockingMono() {
    }

    /**
     * 订阅后在 daemon 线程执行 {@code call}；dispose/cancel 只丢弃结果，不中断线程。
     *
     * @param call 阻塞工作
     * @param <T>  结果类型
     * @return Mono
     */
    public static <T> Mono<T> fromCallable(Callable<T> call) {
        return Mono.create(sink -> {
            Thread worker = new Thread(() -> {
                try {
                    sink.success(call.call());
                } catch (Throwable e) {
                    sink.error(e instanceof RuntimeException re ? re : new RuntimeException(e));
                }
            }, "wuji-chat-io-" + SEQ.incrementAndGet());
            worker.setDaemon(true);
            worker.start();
            sink.onCancel(() -> log.warn(
                    "subscriber cancelled; {} continues (non-stream response may still complete)",
                    worker.getName()));
        });
    }
}
