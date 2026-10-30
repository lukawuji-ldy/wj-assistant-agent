package com.wuji.assistant.common.token;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.Locale;
import java.util.function.Function;

/**
 * OpenAI tiktoken 计数。模型名能对上时用对应词表，否则用 ChatGPT 的 {@code cl100k_base}。
 *
 * @author liudy
 */
public final class OpenAiTokenCounters {

    public static final String CL100K_BASE = "cl100k_base";
    public static final String O200K_BASE = "o200k_base";

    private static final EncodingRegistry REGISTRY = Encodings.newLazyEncodingRegistry();
    private static volatile Encoding cl100k;
    private static volatile Encoding o200k;

    private OpenAiTokenCounters() {
    }

    /**
     * 一行模型配置解析出的词表。
     *
     * @param encoding   {@code cl100k_base} 或 {@code o200k_base}
     * @param exactMatch 列值或模型名前缀能对上内置词表
     */
    public record Selection(String encoding, boolean exactMatch) {
    }

    /**
     * 列值优先，否则按模型名推断。无法识别时用 {@code cl100k_base}，且 {@code exactMatch=false}。
     *
     * @param model         llm_config.model，可空
     * @param tokenEncoding llm_config.token_encoding，可空
     * @return 词表选择
     */
    public static Selection select(String model, String tokenEncoding) {
        String column = normalize(tokenEncoding);
        if (column != null) {
            if (CL100K_BASE.equals(column) || O200K_BASE.equals(column)) {
                return new Selection(column, true);
            }
            return new Selection(CL100K_BASE, false);
        }
        return selectFromModel(model);
    }

    /**
     * 按 encoding 名取计数函数。未知名字按 {@code cl100k_base}。
     *
     * @param encoding 词表名
     * @return 文本 → token 数
     */
    public static Function<String, Integer> counter(String encoding) {
        Encoding chosen = O200K_BASE.equals(normalize(encoding)) ? o200k() : cl100k();
        return text -> chosen.countTokens(text == null ? "" : text);
    }

    private static Selection selectFromModel(String model) {
        String name = normalize(model);
        if (name == null) {
            return new Selection(CL100K_BASE, false);
        }
        if (startsWithAny(name, "gpt-4o", "gpt-4.1", "gpt-5", "chatgpt-4o", "o1", "o3", "o4")) {
            return new Selection(O200K_BASE, true);
        }
        if (startsWithAny(name, "gpt-4", "gpt-3.5", "gpt-35", "text-embedding-3", "text-embedding-ada")) {
            return new Selection(CL100K_BASE, true);
        }
        return new Selection(CL100K_BASE, false);
    }

    private static boolean startsWithAny(String name, String... prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Encoding cl100k() {
        Encoding local = cl100k;
        if (local == null) {
            synchronized (OpenAiTokenCounters.class) {
                local = cl100k;
                if (local == null) {
                    local = REGISTRY.getEncoding(EncodingType.CL100K_BASE);
                    cl100k = local;
                }
            }
        }
        return local;
    }

    private static Encoding o200k() {
        Encoding local = o200k;
        if (local == null) {
            synchronized (OpenAiTokenCounters.class) {
                local = o200k;
                if (local == null) {
                    local = REGISTRY.getEncoding(EncodingType.O200K_BASE);
                    o200k = local;
                }
            }
        }
        return local;
    }
}
