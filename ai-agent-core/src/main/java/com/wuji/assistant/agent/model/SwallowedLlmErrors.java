package com.wuji.assistant.agent.model;

import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.util.StringUtils;

/**
 * Spring AI Alibaba {@code AgentLlmNode} 捕获 LLM 异常后写成
 * {@code AssistantMessage("Exception: " + e.getMessage())}，调用方会当成正常回复。
 * 本类识别该形态并还原为可重试 / 可 failover 的业务异常。
 *
 * @author liudy
 */
public final class SwallowedLlmErrors {

    /** AgentLlmNode 写入助手文本的前缀 */
    public static final String PREFIX = "Exception: ";

    private SwallowedLlmErrors() {
    }

    /**
     * 是否为框架吞掉的 LLM 异常文本。
     *
     * @param text 助手文本
     * @return true 表示应视为失败而非回复
     */
    public static boolean isSwallowed(String text) {
        return StringUtils.hasText(text) && text.startsWith(PREFIX);
    }

    /**
     * 将吞掉的异常文本还原为业务异常；非该形态返回 null。
     *
     * @param text 助手文本
     * @return 业务异常或 null
     */
    public static RuntimeException toException(String text) {
        if (!isSwallowed(text)) {
            return null;
        }
        String inner = text.substring(PREFIX.length()).trim();
        return new WujiException(mapInner(inner), text);
    }

    private static ErrorCode mapInner(String inner) {
        String lower = inner == null ? "" : inner.toLowerCase();
        if (lower.startsWith("429")
                || lower.contains("rate limit")
                || lower.contains("ratelimit")
                || lower.contains("too many")
                || lower.contains("qpm limit")) {
            return ErrorCode.MODEL_RATE_LIMITED;
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return ErrorCode.MODEL_TIMEOUT;
        }
        return ErrorCode.MODEL_UNAVAILABLE;
    }
}
