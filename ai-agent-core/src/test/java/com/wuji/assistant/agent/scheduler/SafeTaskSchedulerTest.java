package com.wuji.assistant.agent.scheduler;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 队列满时丢弃任务，不在调用线程补跑。
 *
 * @author liudy
 */
class SafeTaskSchedulerTest {

    @Test
    void queueFull_dropsTask() throws Exception {
        Scheduler scheduler = Schedulers.newBoundedElastic(1, 1, "reject-test");
        CountDownLatch hold = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean overflowRan = new AtomicBoolean(false);
        try {
            scheduler.schedule(() -> {
                started.countDown();
                try {
                    hold.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            scheduler.schedule(() -> {
            });
            SafeTaskScheduler.submit(scheduler, () -> overflowRan.set(true), "overflow");
            assertFalse(overflowRan.get());
        } finally {
            hold.countDown();
            scheduler.dispose();
        }
    }
}
