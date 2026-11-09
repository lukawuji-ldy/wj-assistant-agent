package com.wuji.assistant.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 短期窗口上限派生规则：compress-message-threshold + keep-recent-messages。
 *
 * @author liudy
 */
class WujiMemoryPropertiesTest {

    @Test
    void windowSize_derivesFromThresholdAndKeep() {
        WujiMemoryProperties.ShortTerm shortTerm = new WujiMemoryProperties.ShortTerm();
        shortTerm.setCompressMessageThreshold(30);
        shortTerm.setKeepRecentMessages(10);
        assertEquals(40, shortTerm.windowSize());
    }
}
