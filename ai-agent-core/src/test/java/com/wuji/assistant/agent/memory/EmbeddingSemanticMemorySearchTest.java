package com.wuji.assistant.agent.memory;

import com.wuji.assistant.memory.model.UserSemanticHit;
import com.wuji.assistant.memory.repo.UserSemanticMemoryRepository;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmbeddingSemanticMemorySearch。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingSemanticMemorySearchTest {

    @Mock
    private UserSemanticMemoryRepository repository;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private ObjectProvider<EmbeddingClient> embeddingClientProvider;

    @Test
    void search_embeddingUnavailable_returnsEmpty() {
        when(embeddingClientProvider.getIfAvailable(org.mockito.ArgumentMatchers.any()))
                .thenReturn(embeddingClient);
        when(embeddingClient.available()).thenReturn(false);
        EmbeddingSemanticMemorySearch search = new EmbeddingSemanticMemorySearch(
                repository, embeddingClientProvider);
        List<UserSemanticHit> hits = search.search("u1", "记得吗", 4, 0.55);
        assertTrue(hits.isEmpty());
        verify(repository, never()).searchSimilar(anyString(), anyString(), anyInt(), anyDouble());
    }

    @Test
    void search_embedsAndQueriesRepo() {
        when(embeddingClientProvider.getIfAvailable(org.mockito.ArgumentMatchers.any()))
                .thenReturn(embeddingClient);
        when(embeddingClient.available()).thenReturn(true);
        float[] vector = new float[UserSemanticMemoryRepository.EXPECTED_DIMENSIONS];
        vector[0] = 0.1f;
        when(embeddingClient.embed("记得吗")).thenReturn(vector);
        UserSemanticHit hit = new UserSemanticHit();
        hit.setId("id1");
        hit.setContent("内容");
        hit.setScore(0.9);
        when(repository.searchSimilar(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(hit));
        EmbeddingSemanticMemorySearch search = new EmbeddingSemanticMemorySearch(
                repository, embeddingClientProvider);
        List<UserSemanticHit> hits = search.search("u1", "记得吗", 4, 0.55);
        assertEquals(1, hits.size());
        assertEquals("id1", hits.get(0).getId());
        verify(repository).touchLastUsed(org.mockito.ArgumentMatchers.eq("u1"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void toVectorLiteral_formats() {
        assertEquals("[1.0,2.5]", EmbeddingSemanticMemorySearch.toVectorLiteral(new float[]{1f, 2.5f}));
    }
}
