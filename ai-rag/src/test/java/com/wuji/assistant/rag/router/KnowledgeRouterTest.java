package com.wuji.assistant.rag.router;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库说明向量与查询的余弦选库。
 *
 * @author liudy
 */
class KnowledgeRouterTest {

    @Test
    void selectsTopTwoAboveMinScore() {
        float[] query = {1f, 0f};
        List<KnowledgeRouter.Routed> selected = KnowledgeRouter.select(query, List.of(
                new KnowledgeRouter.Profile("hr", new float[]{1f, 0f}),
                new KnowledgeRouter.Profile("legal", new float[]{0.8f, 0.6f}),
                new KnowledgeRouter.Profile("finance", new float[]{0f, 1f})
        ), 2, 0.6);

        assertEquals(List.of("hr", "legal"), selected.stream().map(KnowledgeRouter.Routed::collection).toList());
        assertTrue(selected.get(0).score() > selected.get(1).score());
        assertTrue(selected.stream().noneMatch(item -> item.score() < 0.6));
    }

    @Test
    void dropsEveryoneBelowThreshold() {
        List<KnowledgeRouter.Routed> selected = KnowledgeRouter.select(
                new float[]{1f, 0f},
                List.of(new KnowledgeRouter.Profile("finance", new float[]{0f, 1f})),
                2,
                0.6);
        assertTrue(selected.isEmpty());
    }
}
