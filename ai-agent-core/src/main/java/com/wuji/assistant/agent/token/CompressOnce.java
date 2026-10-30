package com.wuji.assistant.agent.token;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本轮是否已经为超限提交过压缩。放在 metadata 里，同一次对话只允许提交一次。
 *
 * @author liudy
 */
public final class CompressOnce {

    public static final String META_KEY = "wuji.compressLatch";

    private final AtomicBoolean started = new AtomicBoolean(false);

    public boolean tryStart() {
        return started.compareAndSet(false, true);
    }
}
