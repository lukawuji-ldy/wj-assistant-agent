package com.wuji.assistant.server.memory;

import com.wuji.assistant.agent.config.WujiMemoryProperties;
import com.wuji.assistant.memory.lifecycle.MemoryConsolidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * L3 定时整理任务。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(prefix = "wuji.memory.lifecycle", name = "consolidate-enabled", havingValue = "true", matchIfMissing = true)
public class MemoryConsolidateScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidateScheduler.class);

    private final MemoryConsolidator consolidator;

    public MemoryConsolidateScheduler(MemoryConsolidator consolidator) {
        this.consolidator = consolidator;
    }

    @Scheduled(cron = "${wuji.memory.lifecycle.consolidate-cron:0 0 3 * * ?}")
    public void run() {
        try {
            int n = consolidator.consolidate(null);
            log.info("scheduled memory consolidate done affected={}", n);
        } catch (Exception e) {
            log.warn("scheduled memory consolidate failed: {}", e.toString());
        }
    }
}
