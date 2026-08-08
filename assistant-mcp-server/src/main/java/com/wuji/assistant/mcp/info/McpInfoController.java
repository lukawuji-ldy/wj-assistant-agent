package com.wuji.assistant.mcp.info;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HexFormat;

/**
 * MCP 工具目录信息（本期：仅支持 Server Version + toolHash + capabilities=tools）。
 */
@RestController
@RequestMapping("/mcp")
public class McpInfoController {

    private final ObjectMapper objectMapper;
    private final String serverName;
    private final String serverVersion;
    private final String protocol;
    private final String build;

    private final ToolCallbackProvider sampleTools;
    private final ToolCallbackProvider openMeteoTools;
    private final ToolCallbackProvider timeTools;

    public McpInfoController(ObjectMapper objectMapper,
                              @Value("${spring.ai.mcp.server.name:wuji-mcp-server}") String serverName,
                              @Value("${spring.ai.mcp.server.version:1.0.0}") String serverVersion,
                              @Value("${wuji.mcp.server.protocol:sdk}") String protocol,
                              @Value("${wuji.mcp.server.build:}") String build,
                              @Qualifier("sampleTools") ToolCallbackProvider sampleTools,
                              @Qualifier("openMeteoTools") ToolCallbackProvider openMeteoTools,
                              @Qualifier("timeTools") ToolCallbackProvider timeTools) {
        this.objectMapper = objectMapper;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.protocol = protocol;
        this.build = build;
        this.sampleTools = sampleTools;
        this.openMeteoTools = openMeteoTools;
        this.timeTools = timeTools;
    }

    @GetMapping("/info")
    public McpInfoResponse info() {
        List<ToolDefinitionDigest> toolDigests = new ArrayList<>();
        collectToolDefinitions(sampleTools, toolDigests);
        collectToolDefinitions(openMeteoTools, toolDigests);
        collectToolDefinitions(timeTools, toolDigests);

        toolDigests.sort(Comparator.comparing(ToolDefinitionDigest::name));
        String toolHashInput = toolDigests.stream()
                .map(ToolDefinitionDigest::toHashInput)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String toolHash = sha256Hex(toolHashInput);
        List<McpInfoResponse.ToolSummary> tools = toolDigests.stream()
                .map(d -> new McpInfoResponse.ToolSummary(
                        d.name(),
                        d.description(),
                        parseSchemaNode(d.inputSchema())))
                .toList();
        return new McpInfoResponse(
                serverName,
                serverVersion,
                protocol,
                build.isBlank() ? null : build,
                List.of("tools"),
                toolHash,
                tools
        );
    }

    private JsonNode parseSchemaNode(String normalizedOrRaw) {
        if (normalizedOrRaw == null || normalizedOrRaw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(normalizedOrRaw);
        } catch (Exception ex) {
            return objectMapper.getNodeFactory().textNode(normalizedOrRaw);
        }
    }

    private void collectToolDefinitions(ToolCallbackProvider provider, List<ToolDefinitionDigest> out) {
        ToolCallback[] callbacks = provider.getToolCallbacks();
        if (callbacks == null) {
            return;
        }
        for (ToolCallback cb : callbacks) {
            if (cb == null || cb.getToolDefinition() == null) {
                continue;
            }
            ToolDefinition def = cb.getToolDefinition();
            out.add(ToolDefinitionDigest.from(def, objectMapper));
        }
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute toolHash", ex);
        }
    }

    private record ToolDefinitionDigest(String name, String description, String inputSchema) {
        static ToolDefinitionDigest from(ToolDefinition def, ObjectMapper objectMapper) {
            String name = safe(def.name());
            String description = safe(def.description());
            String inputSchema = safe(def.inputSchema());
            // inputSchema 本期直接做 JSON 规范化；若无法解析则回退原字符串。
            inputSchema = normalizeJsonOrFallback(objectMapper, inputSchema);
            return new ToolDefinitionDigest(name, description, inputSchema);
        }

        String toHashInput() {
            return name + "|" + description + "|" + inputSchema;
        }

        static String safe(String v) {
            return v == null ? "" : v;
        }
    }

    private static String normalizeJsonOrFallback(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            // toString uses Jackson internal ordering; if schema changes, hash still changes.
            return node.toString();
        } catch (Exception ex) {
            return raw;
        }
    }
}

