package com.wuji.assistant.mcp.config;

import com.wuji.assistant.mcp.tools.SampleToolService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Server 样例工具注册单测。
 *
 * @author liudy
 */
class McpServerConfigurationTest {

    @Test
    void sampleTools_exposesEchoPingOnly() {
        McpServerConfiguration configuration = new McpServerConfiguration();
        ToolCallbackProvider provider = configuration.sampleTools(new SampleToolService());

        Set<String> names = Arrays.stream(provider.getToolCallbacks())
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertEquals(1, names.size());
        assertTrue(names.contains("echo_ping"));
    }

    @Test
    void beanNames_areDistinct() throws Exception {
        Set<String> beanMethodNames = Arrays.stream(McpServerConfiguration.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.context.annotation.Bean.class))
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertEquals(3, beanMethodNames.size());
        assertTrue(beanMethodNames.contains("sampleTools"));
        assertTrue(beanMethodNames.contains("openMeteoTools"));
        assertTrue(beanMethodNames.contains("timeTools"));
    }

    @Test
    void echoPing_returnsPong() {
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(new SampleToolService())
                .build();
        ToolCallback echo = Arrays.stream(provider.getToolCallbacks())
                .filter(cb -> "echo_ping".equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
        String result = echo.call("{\"message\":\"hi\"}");
        assertTrue(result.contains("pong:"));
        assertTrue(result.contains("hi"));
    }
}
