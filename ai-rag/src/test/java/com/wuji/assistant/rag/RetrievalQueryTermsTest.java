package com.wuji.assistant.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中文问句切词。
 *
 * @author liudy
 */
class RetrievalQueryTermsTest {

    @Test
    void splitsXufengnianQueryIntoNameAndPlace() {
        List<String> terms = RetrievalQueryTerms.terms("徐凤年第一次北莽之行的收获");
        assertTrue(terms.contains("徐凤年"), terms.toString());
        assertTrue(terms.contains("北莽"), terms.toString());
    }

    @Test
    void keepsEnglishTokens() {
        List<String> terms = RetrievalQueryTerms.terms("How to export CRM customer data?");
        assertTrue(terms.contains("How") || terms.contains("export") || terms.contains("CRM"), terms.toString());
    }

    @Test
    void likeLiteralStripsWildcards() {
        assertEquals("abc", RetrievalQueryTerms.likeLiteral("%a_b\\c%"));
    }

    @Test
    void likePatternsIncludeNameAndBeimang() {
        List<String> patterns = KnowledgeRetrievalService.likePatterns("徐凤年第一次北莽之行的收获");
        assertTrue(patterns.stream().anyMatch(p -> p.contains("徐凤年")), patterns.toString());
        assertTrue(patterns.stream().anyMatch(p -> p.contains("北莽")), patterns.toString());
    }

    @Test
    void mergeHitsKeepsKeywordOnlyAndDoesNotLetKeywordScoreCoverCosine() {
        RetrievalResult.Hit cosine = new RetrievalResult.Hit("b", "重叠", 0.80, Map.of());
        RetrievalResult.Hit strongerCosine = new RetrievalResult.Hit("a", "仅余弦", 0.91, Map.of());
        RetrievalResult.Hit keywordOverlap = new RetrievalResult.Hit("b", "重叠关键词", 1.0, Map.of());
        RetrievalResult.Hit keywordOnly = new RetrievalResult.Hit("c", "仅关键词", 1.0, Map.of());
        List<RetrievalResult.Hit> merged = KnowledgeRetrievalService.mergeHits(
                List.of(strongerCosine, cosine),
                List.of(keywordOverlap, keywordOnly),
                5);
        assertEquals("b", merged.get(0).chunkId());
        assertEquals(0.80, merged.get(0).score(), 1e-6);
        assertEquals("a", merged.get(1).chunkId());
        assertEquals(0.91, merged.get(1).score(), 1e-6);
        assertEquals("c", merged.get(2).chunkId());
        assertEquals(0.5, merged.get(2).score(), 1e-6);
    }
}
