package com.wuji.assistant.agent.token;

import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.springframework.stereotype.Component;

/**
 * 按 CHAT 行计算本次输入预算。输入预算只有 {@code context_window_size - output_max_tokens}。
 *
 * @author liudy
 */
@Component
public class InputBudgetCalculator {

    /**
     * {@code output_max_tokens} 为空、小于等于 0，或大于等于窗口时，这行配置不可用。
     *
     * @param cfg CHAT 行
     * @return 本次请求的输入预算
     */
    public InputBudget calculate(LlmConfigRecord cfg) {
        String configId = cfg == null || cfg.getConfigId() == null ? "" : cfg.getConfigId();
        int window = cfg == null || cfg.getContextWindowSize() == null ? 0 : cfg.getContextWindowSize();
        Integer outputMaxTokens = cfg == null ? null : cfg.getOutputMaxTokens();
        int reserve = outputMaxTokens == null ? 0 : outputMaxTokens;
        if (outputMaxTokens == null || reserve <= 0 || window <= 0 || reserve >= window) {
            throw new WujiException(ErrorCode.BAD_REQUEST,
                    "LLM 配置 " + configId + " 的 context_window_size 或 output_max_tokens 不可用");
        }
        return new InputBudget(configId, window, reserve, window - reserve);
    }

    /**
     * 一次请求的输入预算。工具 schema 在入模计数时另扣，不写进本记录。
     *
     * @param configId        配置键
     * @param window          输入窗口 W
     * @param outputMaxTokens 输出预留 R，同时是发给模型的 max_tokens
     * @param inputBudget     {@code W - R}
     */
    public record InputBudget(String configId, int window, int outputMaxTokens, int inputBudget) {
    }
}
