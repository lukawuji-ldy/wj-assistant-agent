package com.wuji.assistant.agent.token;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本轮精确检索缓存。键是会话、规范化查询、领域和库。
 *
 * @author liudy
 */
public final class TurnRetrievalCache {

    public static final String META_KEY = "wuji.turnRetrievalCache";

    /** 领域子 Agent 最终文本的库占位，区别于某一库的检索结果。 */
    public static final String DOMAIN_RESULT = "*";

    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();

    public String get(String key) {
        return key == null ? null : values.get(key);
    }

    public void put(String key, String value) {
        if (key != null && value != null) {
            values.put(key, value);
        }
    }

    public static String key(String conversationId, String query, String domain, String collection) {
        return text(conversationId) + "\n" + normalize(query) + "\n" + text(domain) + "\n" + text(collection);
    }

    public static String normalize(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
