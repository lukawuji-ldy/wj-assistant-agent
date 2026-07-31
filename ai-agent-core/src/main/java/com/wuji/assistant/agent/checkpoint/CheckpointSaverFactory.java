package com.wuji.assistant.agent.checkpoint;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.postgresql.PostgresSaver;
import com.wuji.assistant.agent.config.WujiAgentProperties;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 按配置构建 Checkpoint Saver（PostgresSaver / MemorySaver）。
 *
 * @author liudy
 */
@Component
public class CheckpointSaverFactory {

    private static final Logger log = LoggerFactory.getLogger(CheckpointSaverFactory.class);

    private final WujiAgentProperties agentProperties;
    private volatile BaseCheckpointSaver cached;

    public CheckpointSaverFactory(WujiAgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    /**
     * 获取进程内单例 Saver。
     *
     * @return Saver
     */
    public BaseCheckpointSaver getSaver() {
        BaseCheckpointSaver local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = create();
            }
            return cached;
        }
    }

    private BaseCheckpointSaver create() {
        WujiAgentProperties.Checkpoint cp = agentProperties.getCheckpoint();
        String type = cp.getType() == null ? "postgres" : cp.getType().trim().toLowerCase();
        if ("memory".equals(type)) {
            log.info("Checkpoint saver=MemorySaver");
            return new MemorySaver();
        }
        if (!"postgres".equals(type)) {
            throw new WujiException(ErrorCode.BAD_REQUEST, "不支持的 checkpoint.type: " + type);
        }
        try {
            PostgresSaver saver = PostgresSaver.builder()
                    .host(cp.getHost())
                    .port(cp.getPort())
                    .user(cp.getUsername())
                    .password(cp.getPassword())
                    .database(cp.getDatabase())
                    .createTables(cp.isCreateTables())
                    .dropTablesFirst(false)
                    .build();
            log.info("Checkpoint saver=PostgresSaver host={} db={} createTables={}",
                    cp.getHost(), cp.getDatabase(), cp.isCreateTables());
            return saver;
        } catch (Exception e) {
            throw new WujiException(ErrorCode.INTERNAL_ERROR, "初始化 PostgresSaver 失败: " + e.getMessage(), e);
        }
    }
}
