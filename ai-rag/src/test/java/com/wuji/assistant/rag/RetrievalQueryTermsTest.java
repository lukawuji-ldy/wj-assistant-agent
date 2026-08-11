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
    void mergeHitsPrefersHigherScoreAndKeepsKeywordOnly() {
        RetrievalResult.Hit crm = new RetrievalResult.Hit("c1", "crm", 0.62, Map.of());
        RetrievalResult.Hit xu = new RetrievalResult.Hit("c2", "徐凤年", 1.0, Map.of());
        List<RetrievalResult.Hit> merged = KnowledgeRetrievalService.mergeHits(
                List.of(crm), List.of(xu), 5);
        assertEquals(2, merged.size());
        assertEquals("c2", merged.get(0).chunkId());
        assertEquals("c1", merged.get(1).chunkId());
    }
}
