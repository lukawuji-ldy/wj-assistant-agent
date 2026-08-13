package com.wuji.assistant.vta.graph;

import com.wuji.assistant.agent.model.ModelRouter;
import com.wuji.assistant.agent.model.ModelRouter.AuditContext;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.ai.chat.messages.Message;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单节点硬超时：不把 ModelRouter 重试预算耗进整图 job-timeout。
 */
final class VtaTimedLlm {

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);
    private static final ThreadFactory THREAD_FACTORY = r -> {
        Thread t = new Thread(r, "vta-llm-" + THREAD_SEQ.getAndIncrement());
        t.setDaemon(true);
        return t;
    };
    private static final ExecutorService LLM_EXECUTOR = Executors.newCachedThreadPool(THREAD_FACTORY);

    private VtaTimedLlm() {
    }

    static String call(ModelRouter modelRouter,
                       List<Message> messages,
                       AuditContext audit,
                       Duration timeout) {
        Duration bound = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Duration.ofSeconds(60) : timeout;
        Future<String> future = LLM_EXECUTOR.submit(() -> modelRouter.callContent(messages, audit).value());
        try {
            return future.get(Math.max(1L, bound.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new WujiException(ErrorCode.MODEL_TIMEOUT, "VTA node timed out");
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new WujiException(ErrorCode.MODEL_TIMEOUT, "VTA node interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "VTA LLM failed", ex);
        }
    }
}
