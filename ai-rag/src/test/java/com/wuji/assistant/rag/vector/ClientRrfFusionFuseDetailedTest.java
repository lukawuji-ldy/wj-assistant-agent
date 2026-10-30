package com.wuji.assistant.rag.vector;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 融合明细 {@link ClientRrfFusion#fuseDetailed}：暴露每路 1-based rank 与 RRF contribution。
 */
class ClientRrfFusionFuseDetailedTest {

    @Test
    void exposesRanksAndContributionsPerLane() {
        List<ClientRrfFusion.RankedDoc> lane0 = List.of(
                new ClientRrfFusion.RankedDoc("a", "ha"),
                new ClientRrfFusion.RankedDoc("b", "hb"));
        List<ClientRrfFusion.RankedDoc> lane1 = List.of(
                new ClientRrfFusion.RankedDoc("a", "ha"),
                new ClientRrfFusion.RankedDoc("c", "hc"));

        List<ClientRrfFusion.DetailedFusedDoc> fused = ClientRrfFusion.fuseDetailed(List.of(lane0, lane1), 60, 10);

        ClientRrfFusion.DetailedFusedDoc a = byId(fused, "a");
        assertEquals(List.of(1, 1), a.ranks());
        assertEquals(Arrays.asList(1.0 / 61.0, 1.0 / 61.0), a.contributions());
        assertEquals(2.0 / 61.0, a.rrfScore(), 1e-12);
        assertEquals(1.0, a.normalizedScore(), 1e-12);
        assertEquals("ha", a.contentHash());

        ClientRrfFusion.DetailedFusedDoc b = byId(fused, "b");
        assertEquals(Arrays.asList(2, null), b.ranks());
        assertEquals(1.0 / 62.0, b.contributions().get(0), 1e-12);
        assertEquals(0.0, b.contributions().get(1), 1e-12);
        assertEquals(1.0 / 62.0, b.rrfScore(), 1e-12);
        assertEquals(RrfScoreNormalizer.normalize(1.0 / 62.0, 60), b.normalizedScore(), 1e-12);
        assertEquals("hb", b.contentHash());

        ClientRrfFusion.DetailedFusedDoc c = byId(fused, "c");
        assertEquals(Arrays.asList(null, 2), c.ranks());
        assertEquals(0.0, c.contributions().get(0), 1e-12);
        assertEquals(1.0 / 62.0, c.contributions().get(1), 1e-12);
    }

    @Test
    void matchesFuseOrderAndScores() {
        List<ClientRrfFusion.RankedDoc> lane0 = List.of(
                new ClientRrfFusion.RankedDoc("b", null),
                new ClientRrfFusion.RankedDoc("a", null),
                new ClientRrfFusion.RankedDoc("c", null));
        List<ClientRrfFusion.RankedDoc> lane1 = List.of(
                new ClientRrfFusion.RankedDoc("a", "ha"),
                new ClientRrfFusion.RankedDoc("b", "hb"));

        List<ClientRrfFusion.FusedDoc> plain = ClientRrfFusion.fuse(List.of(lane0, lane1), 60, 10);
        List<ClientRrfFusion.DetailedFusedDoc> detailed = ClientRrfFusion.fuseDetailed(List.of(lane0, lane1), 60, 10);

        assertEquals(plain.size(), detailed.size());
        for (int i = 0; i < plain.size(); i++) {
            assertEquals(plain.get(i).id(), detailed.get(i).id());
            assertEquals(plain.get(i).rrfScore(), detailed.get(i).rrfScore(), 1e-12);
            assertEquals(plain.get(i).normalizedScore(), detailed.get(i).normalizedScore(), 1e-12);
        }
    }

    @Test
    void respectsTopKAndEmptyInputs() {
        List<ClientRrfFusion.RankedDoc> lane0 = List.of(
                new ClientRrfFusion.RankedDoc("a", null),
                new ClientRrfFusion.RankedDoc("b", null));

        assertEquals(1, ClientRrfFusion.fuseDetailed(List.of(lane0), 60, 1).size());
        assertTrue(ClientRrfFusion.fuseDetailed(List.of(), 60, 5).isEmpty());
        assertTrue(ClientRrfFusion.fuseDetailed(List.of(lane0), 60, 0).isEmpty());
        assertTrue(ClientRrfFusion.fuseDetailed(List.of(List.of(), List.of()), 60, 5).isEmpty());
    }

    private static ClientRrfFusion.DetailedFusedDoc byId(List<ClientRrfFusion.DetailedFusedDoc> docs, String id) {
        return docs.stream()
                .filter(d -> d.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 id=" + id));
    }
}
