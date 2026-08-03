package com.wuji.assistant.agent.observability;

/**
 * 当前线程最近一次 LLM 调用的 token 用量（供审计读取）。
 *
 * @author liudy
 */
public final class LlmUsageHolder {

    private static final ThreadLocal<Usage> HOLDER = new ThreadLocal<>();

    private LlmUsageHolder() {
    }

    public static void set(Integer promptTokens, Integer completionTokens) {
        HOLDER.set(new Usage(promptTokens, completionTokens));
    }

    public static Usage getAndClear() {
        Usage u = HOLDER.get();
        HOLDER.remove();
        return u == null ? Usage.EMPTY : u;
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * Token 用量。
     *
     * @param promptTokens     prompt
     * @param completionTokens completion
     */
    public record Usage(Integer promptTokens, Integer completionTokens) {
        public static final Usage EMPTY = new Usage(null, null);
    }
}
