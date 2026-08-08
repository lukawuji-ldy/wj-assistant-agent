package com.wuji.assistant.mcp.info;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.mcp.config.McpServerConfiguration;
import com.wuji.assistant.mcp.tools.OpenMeteoService;
import com.wuji.assistant.mcp.tools.SampleToolService;
import com.wuji.assistant.mcp.tools.TimeService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpInfoControllerTest {

    @Test
    void info_containsServerVersionAndStableToolHash() {
        McpServerConfiguration configuration = new McpServerConfiguration();
        ToolCallbackProvider sampleTools = configuration.sampleTools(new SampleToolService());
        ToolCallbackProvider openMeteoTools = configuration.openMeteoTools(new OpenMeteoService());
        ToolCallbackProvider timeTools = configuration.timeTools(new TimeService());

        McpInfoController controller = new McpInfoController(
                new ObjectMapper(),
                "wuji-mcp-server",
                "1.0.0",
                "sdk",
                "build-1",
                sampleTools,
                openMeteoTools,
                timeTools
        );

        McpInfoResponse r1 = controller.info();
        McpInfoResponse r2 = controller.info();

        assertNotNull(r1);
        assertEquals("wuji-mcp-server", r1.name());
        assertEquals("1.0.0", r1.serverVersion());
        assertEquals("sdk", r1.protocol());
        assertEquals(r1.capabilities(), r2.capabilities());
        assertEquals(r1.toolHash(), r2.toolHash());

        assertTrue(Pattern.matches("[0-9a-f]{64}", r1.toolHash()));
        assertEquals(1, r1.capabilities().size());
        assertEquals("tools", r1.capabilities().get(0));
        assertNotNull(r1.tools());
        assertTrue(r1.tools().stream().anyMatch(t -> t.inputSchema() != null));
    }
}

