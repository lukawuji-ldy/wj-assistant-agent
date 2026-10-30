package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 按业务维度拆分专用 Scheduler Bean。
 * <p>
 * 4 个专用线程池：
 * <ul>
 *   <li>{@code dbScheduler}：轻量 JDBC，对齐 Hikari maximum-pool-size</li>
 *   <li>{@code chatTailScheduler}：聊天尾部 fire-and-forget 任务</li>
 *   <li>{@code kbIngestScheduler}：知识库重 IO 入库</li>
 *   <li>{@code llmCallScheduler}：独立 LLM 调用（摘要压缩、记忆抽取等）</li>
 * </ul>
 *
 * @author liudy
 */
@Configuration
@EnableConfigurationProperties(WujiSchedulerProperties.class)
public class ReactorSchedulerConfig {

    @Bean(destroyMethod = "dispose")
    public Scheduler dbScheduler(WujiSchedulerProperties p) {
        WujiSchedulerProperties.Pool s = p.getDb();
        return Schedulers.newBoundedElastic(
                s.getThreadCap(), s.getQueuedTaskCap(), "db-op",
                60, true
        );
    }

    @Bean(destroyMethod = "dispose")
    public Scheduler chatTailScheduler(WujiSchedulerProperties p) {
        WujiSchedulerProperties.Pool s = p.getChatTail();
        return Schedulers.newBoundedElastic(
                s.getThreadCap(), s.getQueuedTaskCap(), "chat-tail",
                60, true
        );
    }

    @Bean(destroyMethod = "dispose")
    public Scheduler kbIngestScheduler(WujiSchedulerProperties p) {
        WujiSchedulerProperties.Pool s = p.getKbIngest();
        return Schedulers.newBoundedElastic(
                s.getThreadCap(), s.getQueuedTaskCap(), "kb-ingest",
                60, true
        );
    }

    @Bean(destroyMethod = "dispose")
    public Scheduler llmCallScheduler(WujiSchedulerProperties p) {
        WujiSchedulerProperties.Pool s = p.getLlmCall();
        return Schedulers.newBoundedElastic(
                s.getThreadCap(), s.getQueuedTaskCap(), "llm-call",
                60, true
        );
    }

    /**
     * 知识库测试专用：与知识入库池隔离，避免测试长任务拖慢线上入库。
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler kbTestScheduler(WujiSchedulerProperties p) {
        WujiSchedulerProperties.Pool s = p.getKbTest();
        return Schedulers.newBoundedElastic(
                s.getThreadCap(), s.getQueuedTaskCap(), "kb-test",
                60, true
        );
    }
}
