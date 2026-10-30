package com.wuji.assistant.agent.checkpoint;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 子 Agent 跑完后释放线程。线程尚未写出 checkpoint 时，PostgresSaver 会抛「active Thread not found」。
 *
 * @author liudy
 */
public final class ChildCheckpointRelease {

    private static final Logger log = LoggerFactory.getLogger(ChildCheckpointRelease.class);

    private ChildCheckpointRelease() {
    }

    /**
     * @param saver  与主 Agent 相同的 Saver
     * @param config 子线程配置
     */
    public static void releaseQuietly(BaseCheckpointSaver saver, RunnableConfig config) {
        if (saver == null || config == null) {
            return;
        }
        try {
            saver.release(config);
        } catch (Exception ex) {
            log.info("child checkpoint release skipped thread={}: {}",
                    config.threadId().orElse(""), ex.toString());
        }
    }
}
