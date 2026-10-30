package com.wuji.assistant.vta.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@EnableConfigurationProperties(VtaSchedulerProperties.class)
public class VtaReactorSchedulerConfig {

    @Bean(destroyMethod = "dispose")
    public Scheduler vtaDbScheduler(VtaSchedulerProperties p) {
        VtaSchedulerProperties.Pool s = p.getDb();
        return Schedulers.newBoundedElastic(
                s.getThreadCap(), s.getQueuedTaskCap(), "vta-db",
                60, true, true
        );
    }

    @Bean(destroyMethod = "dispose")
    public Scheduler vtaLlmCallScheduler(VtaSchedulerProperties p) {
        VtaSchedulerProperties.Pool s = p.getLlmCall();
        return Schedulers.newBoundedElastic(
                s.getThreadCap(), s.getQueuedTaskCap(), "vta-llm",
                60, true, true
        );
    }
}
