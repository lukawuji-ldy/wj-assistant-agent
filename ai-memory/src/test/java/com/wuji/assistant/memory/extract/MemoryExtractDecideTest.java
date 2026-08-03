package com.wuji.assistant.memory.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MemoryExtract 规则单测（不依赖 JDBC）。
 *
 * @author liudy
 */
class MemoryExtractDecideTest {

    @Test
    void decide_nameAndPreferenceAndIgnore() {
        MemoryExtractService svc = new MemoryExtractService(null);
        MemoryActionItem name = svc.decide("我叫张三，请多关照");
        assertEquals(MemoryAction.UPDATE, name.action());
        assertEquals("PROFILE", name.resultType());
        assertEquals("display_name", name.memoryKey());
        assertEquals("张三", name.memoryValue());

        MemoryActionItem pref = svc.decide("我喜欢简洁回答");
        assertEquals(MemoryAction.UPDATE, pref.action());
        assertEquals("PREFERENCE", pref.resultType());

        MemoryActionItem ignore = svc.decide("今天天气怎么样");
        assertEquals(MemoryAction.IGNORE, ignore.action());

        MemoryActionItem remember = svc.decide("记住：我用 Java 17");
        assertEquals(MemoryAction.UPDATE, remember.action());
        assertEquals("preference.explicit", remember.memoryKey());
    }
}
