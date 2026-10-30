package com.wuji.assistant.server.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仅当 JVM 挂了 JDWP（IDE Debug）时拉长连接空闲与模型读超时。
 * 普通启动与线上进程没有调试参数，仍使用 {@code application.yml}。
 *
 * @author liudy
 */
public class LocalDebugTimeoutEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "localDebugTimeouts";

    /** 调试会话内不触发；正数，避免被 HTTP 客户端把 0 折回 60s。 */
    static final String DEBUG_TIMEOUT = "7d";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        apply(environment, ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    void apply(ConfigurableEnvironment environment, List<String> inputArguments) {
        if (!attachedDebugger(inputArguments)) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, timeoutOverrides()));
    }

    static boolean attachedDebugger(List<String> inputArguments) {
        if (inputArguments == null) {
            return false;
        }
        for (String arg : inputArguments) {
            if (arg != null && (arg.contains("jdwp") || arg.contains("-Xdebug"))) {
                return true;
            }
        }
        return false;
    }

    static Map<String, Object> timeoutOverrides() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("server.netty.idle-timeout", DEBUG_TIMEOUT);
        map.put("wuji.model.timeout", DEBUG_TIMEOUT);
        return map;
    }
}
