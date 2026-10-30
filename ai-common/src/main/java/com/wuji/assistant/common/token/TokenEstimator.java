package com.wuji.assistant.common.token;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * 调用前的文本 Token 计数。
 * <p>
 * 已登记的模型走 tiktoken（见 {@link OpenAiTokenCounters}）。未登记时用 ChatGPT 的
 * {@code cl100k_base}。分词器失败时退回启发式，并累计 {@link #providerFailureCount()}。
 * {@link #estimate(String)} 使用主模型；{@link #estimate(String, String)} 使用指定 configId。
 * 费用以 API 返回的 usage 写入 {@code llm_call_log} 为准。
 * <p>
 * 启发式只作降级：空白不计；汉字/假名/韩文每字 {@value #CJK_PER_CHAR}；字母按词
 * {@code max(1, 字符数 / 4)}；数字串 {@code max(1, 位数 / 3)}；标点每个码点 1。
 * 非空启发式结果至少为 1；分词器返回值原样采用（含 0）。
 *
 * @author liudy
 */
public final class TokenEstimator {

    static final double CJK_PER_CHAR = 1.2;

    private static final double LETTERS_PER_TOKEN = 4.0;
    private static final double DIGITS_PER_TOKEN = 3.0;
    private static final double MARK_PER_CHAR = 1.0;
    private static final String DEFAULT_TOKENIZER_ID = OpenAiTokenCounters.CL100K_BASE + ":default";

    private static final Logger LOG = Logger.getLogger(TokenEstimator.class.getName());

    private static final ConcurrentHashMap<String, ProviderBinding> bindings = new ConcurrentHashMap<>();
    private static volatile String primaryConfigId;
    private static volatile ProviderBinding override;
    private static volatile ProviderBinding defaultBinding;
    private static final AtomicLong providerFailures = new AtomicLong();

    private TokenEstimator() {
    }

    /**
     * 覆盖 {@link #estimate(String)} 使用的计数器，不进入 configId 登记表。
     * {@code counter == null} 时只去掉覆盖。
     *
     * @param tokenizerId 分词器标识，不能为空
     * @param counter     文本 → token 数；返回负数或 null 视为失败并降级
     */
    public static void setProvider(String tokenizerId, Function<String, Integer> counter) {
        if (counter == null) {
            override = null;
            return;
        }
        if (tokenizerId == null || tokenizerId.isBlank()) {
            throw new IllegalArgumentException("tokenizerId must identify the model tokenizer");
        }
        override = new ProviderBinding(tokenizerId.trim(), counter);
    }

    /**
     * 登记某个 llm_config.config_id 的计数器。{@code counter == null} 时移除该行。
     *
     * @param configId    配置业务键
     * @param tokenizerId 分词器标识
     * @param counter     文本 → token 数
     */
    public static void register(String configId, String tokenizerId, Function<String, Integer> counter) {
        if (configId == null || configId.isBlank()) {
            throw new IllegalArgumentException("configId is required");
        }
        String key = configId.trim();
        if (counter == null) {
            bindings.remove(key);
            return;
        }
        if (tokenizerId == null || tokenizerId.isBlank()) {
            throw new IllegalArgumentException("tokenizerId must identify the model tokenizer");
        }
        bindings.put(key, new ProviderBinding(tokenizerId.trim(), counter));
    }

    /**
     * 指定 {@link #estimate(String)} 使用的主模型 configId。
     *
     * @param configId 主模型配置键；空白则取消
     */
    public static void setPrimaryConfigId(String configId) {
        primaryConfigId = configId == null || configId.isBlank() ? null : configId.trim();
    }

    public static void clearProvider() {
        override = null;
        primaryConfigId = null;
        bindings.clear();
    }

    /**
     * {@link #estimate(String)} 实际使用的分词器标识。未登记时为 {@code cl100k_base:default}。
     */
    public static String providerId() {
        if (override != null) {
            return override.tokenizerId();
        }
        ProviderBinding primary = primaryBinding();
        if (primary != null) {
            return primary.tokenizerId();
        }
        return DEFAULT_TOKENIZER_ID;
    }

    /**
     * 精确计数失败并降级的累计次数（进程内单调递增，直到 {@link #resetProviderFailureCount()}）。
     */
    public static long providerFailureCount() {
        return providerFailures.get();
    }

    static void resetProviderFailureCount() {
        providerFailures.set(0);
    }

    public static int estimate(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        ProviderBinding current = override != null ? override : primaryBinding();
        if (current == null) {
            current = defaultChatGpt();
        }
        Integer counted = apply(current, content);
        if (counted != null) {
            return counted;
        }
        return Math.max(1, (int) Math.round(heuristic(content)));
    }

    /**
     * 按指定模型配置计数。该行未登记或计数失败时，退回 {@link #estimate(String)}。
     *
     * @param content  文本
     * @param configId llm_config.config_id
     * @return token 数
     */
    public static int estimate(String content, String configId) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        if (configId != null && !configId.isBlank()) {
            ProviderBinding bound = bindings.get(configId.trim());
            if (bound != null) {
                Integer counted = apply(bound, content);
                if (counted != null) {
                    return counted;
                }
            }
        }
        return estimate(content);
    }

    private static ProviderBinding primaryBinding() {
        String id = primaryConfigId;
        if (id == null) {
            return null;
        }
        return bindings.get(id);
    }

    private static ProviderBinding defaultChatGpt() {
        ProviderBinding local = defaultBinding;
        if (local == null) {
            synchronized (TokenEstimator.class) {
                local = defaultBinding;
                if (local == null) {
                    local = new ProviderBinding(
                            DEFAULT_TOKENIZER_ID,
                            OpenAiTokenCounters.counter(OpenAiTokenCounters.CL100K_BASE));
                    defaultBinding = local;
                }
            }
        }
        return local;
    }

    private static Integer apply(ProviderBinding current, String content) {
        try {
            Integer value = current.counter().apply(content);
            if (value != null && value >= 0) {
                return value;
            }
            recordProviderFailure(current.tokenizerId(), value == null ? "null" : "negative");
        } catch (Exception ex) {
            recordProviderFailure(current.tokenizerId(), ex.getClass().getSimpleName());
        }
        return null;
    }

    static double heuristic(String content) {
        if (content == null || content.isEmpty()) {
            return 0.0;
        }
        double tokens = 0.0;
        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            if (Character.isWhitespace(codePoint)) {
                i += Character.charCount(codePoint);
                continue;
            }
            if (isCjk(codePoint)) {
                tokens += CJK_PER_CHAR;
                i += Character.charCount(codePoint);
                continue;
            }
            if (Character.isLetter(codePoint)) {
                int letters = 0;
                while (i < content.length()) {
                    int cp = content.codePointAt(i);
                    if (Character.isWhitespace(cp) || isCjk(cp) || !Character.isLetter(cp)) {
                        break;
                    }
                    letters++;
                    i += Character.charCount(cp);
                }
                tokens += letterRun(letters);
                continue;
            }
            if (Character.isDigit(codePoint)) {
                int digits = 0;
                while (i < content.length()) {
                    int cp = content.codePointAt(i);
                    if (!Character.isDigit(cp)) {
                        break;
                    }
                    digits++;
                    i += Character.charCount(cp);
                }
                tokens += digitRun(digits);
                continue;
            }
            tokens += MARK_PER_CHAR;
            i += Character.charCount(codePoint);
        }
        return tokens;
    }

    private static double letterRun(int letters) {
        if (letters <= 0) {
            return 0.0;
        }
        return Math.max(1.0, letters / LETTERS_PER_TOKEN);
    }

    private static double digitRun(int digits) {
        if (digits <= 0) {
            return 0.0;
        }
        return Math.max(1.0, digits / DIGITS_PER_TOKEN);
    }

    private static void recordProviderFailure(String tokenizerId, String reason) {
        long n = providerFailures.incrementAndGet();
        if (n == 1 || n == 10 || n == 100 || n % 1000 == 0) {
            LOG.warning("token provider failed tokenizerId=" + tokenizerId
                    + " failures=" + n + " reason=" + reason);
        }
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(codePoint);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
                || ub == Character.UnicodeBlock.HIRAGANA
                || ub == Character.UnicodeBlock.KATAKANA
                || ub == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    private record ProviderBinding(String tokenizerId, Function<String, Integer> counter) {
    }
}
