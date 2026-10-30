package com.wuji.assistant.agent.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Fire-and-Forget 任务安全提交：异常兜底、超时 watchdog、队列拒绝保护。
 *
 * @author liudy
 */
public final class SafeTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(SafeTaskScheduler.class);

    private SafeTaskScheduler() {
    }

    public static Disposable submit(Scheduler scheduler,
                                    Runnable task,
                                    String taskName,
                                    Duration timeout) {
        try {
            Disposable d = scheduler.schedule(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("task={} failed: {}", taskName, e.toString(), e);
                }
            });
            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                scheduler.schedule(
                        () -> {
                            if (!d.isDisposed()) {
                                log.warn("task={} timeout={}ms, disposing",
                                        taskName, timeout.toMillis());
                                d.dispose();
                            }
                        },
                        timeout.toMillis(),
                        TimeUnit.MILLISECONDS
                );
            }
            return d;
        } catch (RejectedExecutionException e) {
            log.error("task={} rejected: scheduler queue full, dropped", taskName, e);
            return null;
        }
    }

    public static Disposable submit(Scheduler scheduler, Runnable task, String taskName) {
        return submit(scheduler, task, taskName, null);
    }
}
