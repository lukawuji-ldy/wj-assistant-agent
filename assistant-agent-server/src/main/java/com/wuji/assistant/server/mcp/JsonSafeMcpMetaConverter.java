package com.wuji.assistant.server.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP {@code tools/call} 的 {@code _meta} 只保留 Jackson 能写成 JSON 的值。
 * <p>
 * ReactAgent 把 {@code RunnableConfig} metadata 放进 {@link ToolContext}。
 * 其中的账本、检索缓存、系统提示持有者和压缩闩没有 Bean 属性，
 * 原样写入 {@code _meta} 会在 SSE 序列化时报 {@code Failed to serialize message}。
 *
 * @author liudy
 */
public final class JsonSafeMcpMetaConverter implements ToolContextToMcpMetaConverter {

    @Override
    public Map<String, Object> convert(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null || toolContext.getContext().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : toolContext.getContext().entrySet()) {
            if (entry.getKey() == null || McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY.equals(entry.getKey())) {
                continue;
            }
            Object value = jsonSafe(entry.getValue());
            if (value != null) {
                safe.put(entry.getKey(), value);
            }
        }
        return safe.isEmpty() ? Map.of() : Map.copyOf(safe);
    }

    /**
     * @return 可进入 JSON 的值；进程内对象返回 null，调用方丢弃该键
     */
    static Object jsonSafe(Object value) {
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            if (number instanceof Double d && !Double.isFinite(d)) {
                return null;
            }
            if (number instanceof Float f && !Float.isFinite(f)) {
                return null;
            }
            return number;
        }
        if (value instanceof Enum<?> enumerated) {
            return enumerated.name();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    continue;
                }
                Object child = jsonSafe(entry.getValue());
                if (child != null) {
                    nested.put(key, child);
                }
            }
            return nested;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> nested = new ArrayList<>();
            for (Object item : collection) {
                Object child = jsonSafe(item);
                if (child != null) {
                    nested.add(child);
                }
            }
            return nested;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> nested = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object child = jsonSafe(Array.get(value, i));
                if (child != null) {
                    nested.add(child);
                }
            }
            return nested;
        }
        return null;
    }
}
