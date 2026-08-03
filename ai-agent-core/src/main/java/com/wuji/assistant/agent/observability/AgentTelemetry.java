package com.wuji.assistant.agent.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Agent 热路径 Observation / Counter 封装（无 Registry 时退化为直调）。
 *
 * @author liudy
 */
@Component
public class AgentTelemetry {

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    public AgentTelemetry(ObjectProvider<ObservationRegistry> observationRegistries,
                          ObjectProvider<MeterRegistry> meterRegistries) {
        this.observationRegistry = observationRegistries.getIfAvailable(() -> ObservationRegistry.NOOP);
        this.meterRegistry = meterRegistries.getIfAvailable(() -> null);
    }

    /**
     * 观测一段同步逻辑。
     *
     * @param name     span 名
     * @param attrs    低基数属性（成对 key,value）
     * @param supplier 业务
     * @param <T>      返回类型
     * @return 业务结果
     */
    public <T> T observe(String name, String[] attrs, Supplier<T> supplier) {
        Observation observation = Observation.createNotStarted(name, observationRegistry);
        applyAttrs(observation, attrs);
        return observation.observe(supplier);
    }

    /**
     * 观测无返回值逻辑。
     *
     * @param name     span 名
     * @param attrs    属性
     * @param runnable 业务
     */
    public void observe(String name, String[] attrs, Runnable runnable) {
        observe(name, attrs, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * failover 计数。
     *
     * @param fromConfig 源配置
     * @param toConfig   目标配置
     */
    public void countFailover(String fromConfig, String toConfig) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("agent.failover")
                .tag("from", fromConfig == null ? "unknown" : fromConfig)
                .tag("to", toConfig == null ? "unknown" : toConfig)
                .register(meterRegistry)
                .increment();
    }

    private static void applyAttrs(Observation observation, String[] attrs) {
        if (attrs == null) {
            return;
        }
        for (int i = 0; i + 1 < attrs.length; i += 2) {
            String k = attrs[i];
            String v = attrs[i + 1];
            if (k != null && v != null) {
                observation.lowCardinalityKeyValue(k, v);
            }
        }
    }
}
