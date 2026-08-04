package com.wuji.assistant.memory.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MemoryExtract 规则单测（不依赖 JDBC）。
 *
 * @author liudy
 */
class MemoryExtractDecideTest {

    private static final String CONTRAST_LONG = "因为我在河北燕郊住，也取得了户口，如果是河北人问，我会说我是河北廊坊燕郊人，但实际上我是山西大同人。";

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

    @Test
    void decide_hometownAndSelfDesc_doNotShareDisplayName() {
        MemoryExtractService svc = new MemoryExtractService(null);

        MemoryActionItem hometown = svc.decide("我是一个山西大同人");
        assertEquals(MemoryAction.UPDATE, hometown.action());
        assertEquals("PROFILE", hometown.resultType());
        assertEquals("hometown", hometown.memoryKey());
        assertEquals("山西大同", hometown.memoryValue());
        assertNotEquals("display_name", hometown.memoryKey());

        MemoryActionItem from = svc.decide("我来自山西大同");
        assertEquals("hometown", from.memoryKey());
        assertEquals("山西大同", from.memoryValue());

        MemoryActionItem selfDesc = svc.decide("我是一个忧郁的男人");
        assertEquals(MemoryAction.UPDATE, selfDesc.action());
        assertEquals("PROFILE", selfDesc.resultType());
        assertEquals("self_desc", selfDesc.memoryKey());
        assertEquals("一个忧郁的男人", selfDesc.memoryValue());

        // 我是 不得再写入 display_name
        MemoryActionItem notName = svc.decide("我是李四");
        assertEquals("self_desc", notName.memoryKey());
        assertEquals("李四", notName.memoryValue());
    }

    @Test
    void decide_contrastPrefersActualHometown_andExtractsResidence() {
        MemoryExtractService svc = new MemoryExtractService(null);

        MemoryActionItem main = svc.decide(CONTRAST_LONG);
        assertEquals(MemoryAction.UPDATE, main.action());
        assertEquals("hometown", main.memoryKey());
        assertEquals("山西大同", main.memoryValue());
        assertNotEquals("河北廊坊燕郊", main.memoryValue());

        assertEquals("河北燕郊", MemoryExtractService.extractResidence(CONTRAST_LONG));
        assertEquals("山西大同", MemoryExtractService.extractHometown(CONTRAST_LONG));
    }

    @Test
    void decide_statedAsAlone_doesNotWriteHometown() {
        MemoryExtractService svc = new MemoryExtractService(null);
        assertNull(MemoryExtractService.extractHometown("我会说我是河北人"));
        MemoryActionItem item = svc.decide("我会说我是河北人");
        assertNotEquals("hometown", item.memoryKey());
    }
}
