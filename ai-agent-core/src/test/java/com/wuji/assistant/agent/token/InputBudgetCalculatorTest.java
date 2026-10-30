package com.wuji.assistant.agent.token;

import com.wuji.assistant.agent.model.LlmConfigRecord;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 输入预算只等于窗口减去 output_max_tokens。
 *
 * @author liudy
 */
class InputBudgetCalculatorTest {

    private final InputBudgetCalculator calculator = new InputBudgetCalculator();

    @Test
    void inputBudget_isWindowMinusMaxTokens() {
        LlmConfigRecord cfg = config(32768, 4096);
        InputBudgetCalculator.InputBudget budget = calculator.calculate(cfg);
        assertEquals(32768, budget.window());
        assertEquals(4096, budget.outputMaxTokens());
        assertEquals(28672, budget.inputBudget());
    }

    @Test
    void missingMaxTokens_fails() {
        WujiException ex = assertThrows(WujiException.class, () -> calculator.calculate(config(32768, null)));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void maxTokensNotBelowWindow_fails() {
        WujiException ex = assertThrows(WujiException.class, () -> calculator.calculate(config(4096, 4096)));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    private static LlmConfigRecord config(int window, Integer maxTokens) {
        LlmConfigRecord cfg = new LlmConfigRecord();
        cfg.setConfigId("llm");
        cfg.setContextWindowSize(window);
        cfg.setOutputMaxTokens(maxTokens);
        return cfg;
    }
}
