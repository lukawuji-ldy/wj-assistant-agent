package com.wuji.assistant.common.token;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token 估算器单元测试。
 * <p>
 * 启发式断言分类行为（词、数字串、汉字、标点），不把某个固定「每字符比例」当成精确规格。
 * 与 cl100k 的误差只用绝对误差观察，避免短文本 MAPE 失真。
 *
 * @author liudy
 */
class TokenEstimatorTest {

    @AfterEach
    void clearProvider() {
        TokenEstimator.clearProvider();
        TokenEstimator.resetProviderFailureCount();
    }

    @Test
    void testEmpty() {
        assertEquals(0, TokenEstimator.estimate(""));
        assertEquals(0, TokenEstimator.estimate((String) null));
        assertEquals(0.0, TokenEstimator.heuristic("   "), 0.001);
    }

    @Test
    void testPureChinese() {
        String s = "你好世界，这是一段测试文本";
        int tokens = TokenEstimator.estimate(s);
        assertTrue(tokens > 0, "tokens should be positive");
        int expectedMin = (int) (s.length() * 0.8);
        int expectedMax = (int) (s.length() * 1.6);
        assertTrue(tokens >= expectedMin && tokens <= expectedMax,
                "tokens=" + tokens + " should within [" + expectedMin + "," + expectedMax + "]");
    }

    @Test
    void testPureEnglish() {
        String s = "Hello world this is a test paragraph with multiple words";
        int tokens = TokenEstimator.estimate(s);
        assertTrue(tokens > 0);
        int words = s.split("\\s+").length;
        assertTrue(tokens <= words * 2, "english token estimate too high: " + tokens);
        assertTrue(tokens >= words, "short english words should not collapse below one token each: " + tokens);
    }

    @Test
    void testMixedCjkEnglish() {
        String s = "Spring AI 是一个 Java 框架，支持多种 LLM 接入，例如 OpenAI、通义千问、DeepSeek 等。"
                + "It provides reactive API for building RAG and Agent applications.";
        int tokens = TokenEstimator.estimate(s);
        assertTrue(tokens > 20);
    }
    @Test
    void testMixedCjkEnglish2() {
//        String sample = "Spring AI";
        String sample = "I am chinese, where are you from ?";
        int tokens = TokenEstimator.estimate(sample);
        int tokens2 = OpenAiTokenCounters.counter(OpenAiTokenCounters.CL100K_BASE).apply(sample);
        int tokens3 = OpenAiTokenCounters.counter(OpenAiTokenCounters.O200K_BASE).apply(sample);
        System.out.println("tokens =  " + tokens);
        System.out.println("tokens2 =  " + tokens2);
        System.out.println("tokens3 =  " + tokens3);
    }

    @Test
    void testCodeAndNumbers() {
        String s = "```java\npublic int add(int a, int b) { return a + b; }\n```\n1234567890";
        int tokens = TokenEstimator.estimate(s);
        assertTrue(tokens > 0);
    }

    @Test
    void testSingleCharNonZero() {
        assertEquals(1, TokenEstimator.estimate("a"));
        assertEquals(1, TokenEstimator.estimate("中"));
    }

    @Test
    void testLongChineseParagraph() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("汉");
        }
        String s = sb.toString();
        double perChar = TokenEstimator.heuristic("汉");
        double tokens = TokenEstimator.heuristic(s);
        int expected = (int) Math.round(perChar * 10000);
        double errorPct = Math.abs(tokens - expected) / (double) expected;
        assertTrue(errorPct < 0.01,
                "1万汉字应与单字比例成正比，tokens=" + tokens + " expected=" + expected);
    }

    @Test
    void testProviderRequiresTokenizerId() {
        assertThrows(IllegalArgumentException.class, () -> TokenEstimator.setProvider("  ", s -> 1));
        assertThrows(IllegalArgumentException.class, () -> TokenEstimator.setProvider(null, s -> 1));
    }

    @Test
    void testProviderOverride() {
        TokenEstimator.setProvider("fixture-tokenizer", s -> 42);
        assertEquals("fixture-tokenizer", TokenEstimator.providerId());
        assertEquals(42, TokenEstimator.estimate("anything"));
        TokenEstimator.setProvider("fixture-tokenizer", s -> 0);
        assertEquals(0, TokenEstimator.estimate("anything"));
    }

    @Test
    void testProviderFailureFallback() {
        TokenEstimator.setProvider("broken-tokenizer", s -> {
            throw new RuntimeException("fail");
        });
        int tokens = TokenEstimator.estimate("中文");
        assertTrue(tokens > 0);
        assertEquals(1, TokenEstimator.providerFailureCount());

        TokenEstimator.setProvider("broken-tokenizer", s -> null);
        assertTrue(TokenEstimator.estimate("中文") > 0);
        assertEquals(2, TokenEstimator.providerFailureCount());
    }

    @Test
    void testLetterRunsRespectWordBoundaries() {
        assertTrue(TokenEstimator.heuristic("I am a") >= 3.0);
        assertTrue(TokenEstimator.heuristic("Iama") < TokenEstimator.heuristic("I am a"));
        assertTrue(TokenEstimator.heuristic("hello") <= 2.0);
        assertTrue(TokenEstimator.heuristic("internationalization") > 1.0);
        assertTrue(TokenEstimator.heuristic("internationalization") < "internationalization".length());
    }

    @Test
    void testDigitRunIsNotPerDigitCodeRatio() {
        assertEquals(3.0, TokenEstimator.heuristic("123456789"), 0.001);
        assertTrue(TokenEstimator.heuristic("123456789") < 9 * 0.8);
        assertEquals(2.0, TokenEstimator.heuristic("12 34"), 0.001);
    }

    @Test
    void testCjkScalesSeparatelyFromPunctuation() {
        double one = TokenEstimator.heuristic("中");
        assertEquals(one * 7, TokenEstimator.heuristic("中华人民共和国"), 0.001);
        assertTrue(one > 1.0 && one < 1.5);
        assertEquals(1.0, TokenEstimator.heuristic("，"), 0.001);
        assertEquals(1.0, TokenEstimator.heuristic(","), 0.001);
        assertTrue(one > TokenEstimator.heuristic("，"));
    }

    @Test
    void testSupplementaryCjkCodePointIsCountedAsCjk() {
        String supplementaryCjk = "\uD840\uDC00";
        assertEquals(TokenEstimator.heuristic("中"), TokenEstimator.heuristic(supplementaryCjk), 0.001);
    }

    @Test
    void testHeuristicAbsoluteErrorAgainstCl100k() {
        Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        String[] samples = {
                "你好世界，这是一段测试文本",
                "Hello world this is a test paragraph with multiple words",
                "I am a Java engineer.",
                "internationalization",
                "Map<String, List<Order>> orderMap = new HashMap<>();",
                "123456789",
                "{\"name\":\"张三\",\"city\":\"北京\"}",
                "Spring AI 是一个 Java 框架，支持多种 LLM 接入。",
                "知识库检索返回以下片段：员工年假为 10 天，需提前三个工作日申请。"
        };
        long absSum = 0;
        int maxAbs = 0;
        StringBuilder detail = new StringBuilder();
        for (String sample : samples) {
            int actual = encoding.countTokens(sample);
            int estimated = Math.max(1, (int) Math.round(TokenEstimator.heuristic(sample)));
            int abs = Math.abs(estimated - actual);
            absSum += abs;
            maxAbs = Math.max(maxAbs, abs);
            detail.append("\n  est=").append(estimated)
                    .append(" cl100k=").append(actual)
                    .append(" text=").append(sample);
        }
        double mae = absSum / (double) samples.length;
        assertTrue(mae <= 8.0, "MAE=" + mae + " maxAbs=" + maxAbs + detail);
        assertTrue(maxAbs <= 20, "maxAbs=" + maxAbs + detail);
    }

    @Test
    void testDefaultEstimateMatchesCl100k() {
        Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        assertEquals(OpenAiTokenCounters.CL100K_BASE + ":default", TokenEstimator.providerId());
        assertEquals(encoding.countTokens("Spring"), TokenEstimator.estimate("Spring"));
        assertEquals(encoding.countTokens("工厂"), TokenEstimator.estimate("工厂"));
    }

    @Test
    void testEstimateUsesRegisteredModel() {
        TokenEstimator.register("llm_primary", "o200k_base:gpt-4o-mini",
                OpenAiTokenCounters.counter(OpenAiTokenCounters.O200K_BASE));
        TokenEstimator.register("llm_backup_1", "cl100k_base:qwen-plus",
                OpenAiTokenCounters.counter(OpenAiTokenCounters.CL100K_BASE));
        TokenEstimator.setPrimaryConfigId("llm_primary");
        String sample = "Spring AI 工厂";
        assertEquals(OpenAiTokenCounters.counter(OpenAiTokenCounters.O200K_BASE).apply(sample),
                TokenEstimator.estimate(sample));
        assertEquals(OpenAiTokenCounters.counter(OpenAiTokenCounters.CL100K_BASE).apply(sample),
                TokenEstimator.estimate(sample, "llm_backup_1"));
        assertEquals(OpenAiTokenCounters.counter(OpenAiTokenCounters.O200K_BASE).apply(sample),
                TokenEstimator.estimate(sample, "missing"));
    }
}
