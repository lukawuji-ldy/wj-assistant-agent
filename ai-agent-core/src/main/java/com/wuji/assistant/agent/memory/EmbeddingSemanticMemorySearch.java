package com.wuji.assistant.agent.memory;

import com.wuji.assistant.memory.model.UserSemanticHit;
import com.wuji.assistant.memory.repo.UserSemanticMemoryRepository;
import com.wuji.assistant.memory.retrieve.SemanticMemorySearchPort;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 语义记忆检索：Embedding + user_semantic_memory 余弦近邻。
 *
 * @author liudy
 */
@Component
public class EmbeddingSemanticMemorySearch implements SemanticMemorySearchPort {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingSemanticMemorySearch.class);

    private final UserSemanticMemoryRepository semanticMemoryRepository;
    private final EmbeddingClient embeddingClient;

    public EmbeddingSemanticMemorySearch(UserSemanticMemoryRepository semanticMemoryRepository,
                                         ObjectProvider<EmbeddingClient> embeddingClients) {
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.embeddingClient = embeddingClients.getIfAvailable(() -> new EmbeddingClient() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public float[] embed(String text) {
                return null;
            }
        });
    }

    @Override
    public List<UserSemanticHit> search(String userId, String query, int topK, double minScore) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(query)) {
            return List.of();
        }
        if (!embeddingClient.available()) {
            log.debug("embedding unavailable, skip semantic memory search");
            return List.of();
        }
        float[] vector = embeddingClient.embed(query.trim());
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        if (vector.length != UserSemanticMemoryRepository.EXPECTED_DIMENSIONS) {
            log.warn("skip semantic search: embedding dim={} expected={}",
                    vector.length, UserSemanticMemoryRepository.EXPECTED_DIMENSIONS);
            return List.of();
        }
        String literal = toVectorLiteral(vector);
        List<UserSemanticHit> hits = semanticMemoryRepository.searchSimilar(
                userId, literal, topK, minScore);
        if (!hits.isEmpty()) {
            try {
                List<String> ids = hits.stream().map(UserSemanticHit::getId).toList();
                semanticMemoryRepository.touchLastUsed(userId, ids);
            } catch (Exception e) {
                log.debug("touch semantic last_used failed: {}", e.toString());
            }
        }
        return hits;
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
