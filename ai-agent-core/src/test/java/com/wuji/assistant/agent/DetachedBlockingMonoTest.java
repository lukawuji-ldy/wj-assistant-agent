package com.wuji.assistant.agent;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP cancel 不得打断阻塞工作线程。
 *
 * @author liudy
 */
class DetachedBlockingMonoTest {

    @Test
    void dispose_doesNotInterruptWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        Mono<String> mono = DetachedBlockingMono.fromCallable(() -> {
            started.countDown();
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                throw e;
            }
            finished.countDown();
            return "ok";
        });

        Disposable d = mono.subscribe();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        d.dispose();
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        assertFalse(interrupted.get());
    }

    @Test
    void emitsResultWhenSubscriberStays() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        DetachedBlockingMono.fromCallable(() -> "hello")
                .subscribe(v -> {
                    value.set(v);
                    done.countDown();
                });
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertEquals("hello", value.get());
    }
}
