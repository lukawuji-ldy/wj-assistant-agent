package com.wuji.assistant.common.token;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 截断工具单元测试。
 *
 * @author liudy
 */
class TruncationUtilsTest {

    @Test
    void testEmptyInput() {
        TruncationUtils.TruncationResult r = TruncationUtils.truncateTail("", 100);
        assertEquals("", r.output());
        assertFalse(r.truncated());
        assertEquals(0, r.originalTokens());
    }

    @Test
    void testNullInput() {
        TruncationUtils.TruncationResult r = TruncationUtils.truncateTail(null, 100);
        assertEquals("", r.output());
        assertFalse(r.truncated());
    }

    @Test
    void testNoTruncation() {
        String s = "Hello 世界";
        int total = TokenEstimator.estimate(s);
        TruncationUtils.TruncationResult r = TruncationUtils.truncateTail(s, total + 100);
        assertFalse(r.truncated());
        assertEquals(s, r.output());
    }

    @Test
    void testTruncateTailChinese() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("中");
        }
        String s = sb.toString();
        int maxTokens = 100;
        TruncationUtils.TruncationResult r = TruncationUtils.truncateTail(s, maxTokens);
        assertTrue(r.truncated());
        assertTrue(r.outputTokens() <= maxTokens + 5,
                "output tokens " + r.outputTokens() + " should close to max " + maxTokens);
        assertTrue(s.startsWith(r.output()));
    }

    @Test
    void testTruncateHeadChinese() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String s = sb.toString();
        int maxTokens = 20;
        TruncationUtils.TruncationResult r = TruncationUtils.truncateHead(s, maxTokens);
        assertTrue(r.truncated());
        assertTrue(s.endsWith(r.output()));
    }

    @Test
    void testTruncateLineByLine() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("第").append(i).append("行：这是一行测试内容\n");
        }
        String s = sb.toString();
        int maxTokens = 80;
        TruncationUtils.TruncationResult r = TruncationUtils.truncateLine(s, maxTokens);
        assertTrue(r.truncated());
        assertTrue(r.outputTokens() <= maxTokens + 20,
                "output tokens too high: " + r.outputTokens());
        assertTrue(r.output().endsWith("\n") || !r.output().contains("\n") ||
                        s.startsWith(r.output()),
                "truncateLine should preserve whole lines");
    }

    @Test
    void testUtf8MultiByteBoundary() {
        String s = "Hello" + "中";
        String result = TruncationUtils.ensureUtf8Boundary(s);
        assertEquals(s, result);
    }

    @Test
    void testMixedTruncationTailPreservesHead() {
//        String s = "abcdefghij中文字符测试ABCDEFGHIJ";
        String s = "are you stupid or something, no body to you like it";
        int max = TokenEstimator.estimate(s) / 2;
        TruncationUtils.TruncationResult r = TruncationUtils.truncateTail(s, max);
        System.out.println(r.output()  + "，max = " + 2);
        assertTrue(r.truncated());
        assertEquals(0, s.indexOf(r.output()), "truncateTail 必须从头保留");
    }

    @Test
    void testMixedTruncationHeadPreservesTail() {
        String s = "abcdefghij中文字符测试ABCDEFGHIJ";
        int max = TokenEstimator.estimate(s) / 2;
        TruncationUtils.TruncationResult r = TruncationUtils.truncateHead(s, max);
        assertTrue(r.truncated());
        assertTrue(s.endsWith(r.output()));
    }

    @Test
    void testResultMetaFields() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append('x');
        String s = sb.toString();
        TruncationUtils.TruncationResult r = TruncationUtils.truncateTail(s, 20);
        assertTrue(r.truncatedByTokens() > 0);
        assertTrue(r.originalTokens() > r.outputTokens());
        assertTrue(r.outputBytes() > 0);
        assertEquals(r.output().getBytes().length, r.outputBytes());
    }

    @Test
    void testVeryLongSingleLineNoTruncateIfUnderLimit() {
        String s = "abc";
        TruncationUtils.TruncationResult r = TruncationUtils.truncateLine(s, 100);
        assertFalse(r.truncated());
        assertEquals(s, r.output());
    }

    @Test
    void testTruncateTailExactZero() {
        TruncationUtils.TruncationResult r = TruncationUtils.truncateTail("something", 0);
        assertTrue(r.truncated());
    }
}
