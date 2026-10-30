package com.wuji.assistant.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalDebugTimeoutEnvironmentPostProcessor} 单测。
 *
 * @author liudy
 */
class LocalDebugTimeoutEnvironmentPostProcessorTest {

    private final LocalDebugTimeoutEnvironmentPostProcessor processor =
            new LocalDebugTimeoutEnvironmentPostProcessor();

    @Test
    void attachedDebugger_matchesJdwpOnly() {
        assertFalse(processor.attachedDebugger(List.of("-Ddebug=true", "-Xmx512m")));
        assertFalse(processor.attachedDebugger(null));
        assertTrue(processor.attachedDebugger(List.of(
                "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=127.0.0.1:5005")));
        assertTrue(processor.attachedDebugger(List.of("-Xdebug")));
    }

    @Test
    void apply_withoutDebugger_keepsYamlDefaults() {
        MockEnvironment env = environmentWithDefaults();
        processor.apply(env, List.of("-javaagent:other"));
        assertEquals("60s", env.getProperty("wuji.model.timeout"));
        assertEquals("10m", env.getProperty("server.netty.idle-timeout"));
        assertNull(env.getPropertySources().get(LocalDebugTimeoutEnvironmentPostProcessor.SOURCE_NAME));
    }

    @Test
    void apply_withJdwp_overridesIdleAndModelTimeout() {
        MockEnvironment env = environmentWithDefaults();
        processor.apply(env, List.of("-agentlib:jdwp=transport=dt_socket"));
        assertEquals("7d", env.getProperty("wuji.model.timeout"));
        assertEquals("7d", env.getProperty("server.netty.idle-timeout"));
    }

    private static MockEnvironment environmentWithDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
                "wuji.model.timeout", "60s",
                "server.netty.idle-timeout", "10m")));
        return env;
    }
}
