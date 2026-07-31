package com.wuji.assistant.common.util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务键与 BIGINT 主键生成器（本地简易雪花）。
 *
 * @author liudy
 */
public final class IdGenerator {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 1000);

    private IdGenerator() {
    }

    /**
     * 生成 BIGINT 主键。
     *
     * @return 单调倾向的 long
     */
    public static long nextLong() {
        long ts = System.currentTimeMillis();
        long seq = SEQUENCE.incrementAndGet() & 0xFFFFF;
        return (ts << 20) | seq;
    }

    /**
     * 生成带前缀的业务字符串 ID。
     *
     * @param prefix 前缀，如 c_ / m_ / s_
     * @return 业务键
     */
    public static String nextBizId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
