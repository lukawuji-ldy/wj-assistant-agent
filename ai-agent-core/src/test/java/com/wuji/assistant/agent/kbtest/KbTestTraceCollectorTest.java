package com.wuji.assistant.agent.kbtest;

import com.wuji.assistant.rag.diagnose.RetrievalTestOptions;
import com.wuji.assistant.rag.diagnose.RetrievalTrace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求内检索轨迹收集：分组、领域顺序、失败降级与跨组 RRF。
 *
 * @author liudy
 */
class KbTestTraceCollectorTest {

    @Test
    void record_groupsDomainsInFirstSeenOrder() {
        KbTestTraceCollector collector = new KbTestTraceCollector(null);
        collector.record("sales", "价格", trace("价格", fused("c1", "h1")));
        collector.record("hr", "假期", trace("假期", fused("c2", "h2")));
        collector.record("sales", "折扣", trace("折扣", fused("c3", "h3")));

        assertEquals(List.of("sales", "hr"), collector.domains());

        List<DomainTrace> traces = collector.domainTraces();
        assertEquals(2, traces.size());
        assertEquals("sales", traces.get(0).domain());
        assertEquals(2, traces.get(0).queries().size());
        assertEquals("hr", traces.get(1).domain());
        assertEquals(1, traces.get(1).queries().size());
    }

    @Test
    void record_nullTrace_isIgnored() {
        KbTestTraceCollector collector = new KbTestTraceCollector(null);
        collector.record("sales", "价格", null);

        assertTrue(collector.domains().isEmpty());
        assertTrue(collector.domainTraces().isEmpty());
    }

    @Test
    void fail_prefixesDomainAndKeepsDefaultForBlankMessage() {
        KbTestTraceCollector collector = new KbTestTraceCollector(null);
        collector.fail("sales", " 检索超时 ");
        collector.fail(null, null);

        assertEquals(List.of("sales：检索超时", "领域执行失败"), collector.failures());
    }

    @Test
    void singleQuery_hasNoGroupFusion() {
        KbTestTraceCollector collector = new KbTestTraceCollector(null);
        collector.record("sales", "价格", trace("价格", fused("c1", "h1")));

        assertTrue(collector.domainTraces().get(0).groupFused().isEmpty());
    }

    @Test
    void multiQuery_buildsCrossGroupFusionRankedBySharedHit() {
        KbTestTraceCollector collector = new KbTestTraceCollector(RetrievalTestOptions.defaults());
        collector.record("sales", "价格", trace("价格", fused("c1", "h1"), fused("c2", "h2")));
        collector.record("sales", "折扣", trace("折扣", fused("c2", "h2"), fused("c3", "h3")));

        DomainTrace domain = collector.domainTraces().get(0);
        assertEquals(2, domain.queries().size());

        List<GroupFusedHit> group = domain.groupFused();
        assertFalse(group.isEmpty());

        // c2 同时命中两个问句组，RRF 贡献最高，应排第一
        GroupFusedHit top = group.get(0);
        assertEquals(1, top.rank());
        assertEquals("c2", top.chunkId());
        assertEquals(2, top.groupRanks().size());
        assertNotNull(top.groupRanks().get(0));
        assertNotNull(top.groupRanks().get(1));
        assertEquals(2, top.groupContributions().size());
    }

    @Test
    void options_defaultWhenNull() {
        KbTestTraceCollector collector = new KbTestTraceCollector(null);
        assertEquals(RetrievalTestOptions.defaults(), collector.options());
    }

    private static RetrievalTrace trace(String query, RetrievalTrace.FusedHit... hits) {
        return new RetrievalTrace(
                "pgvector",
                "sales",
                query,
                RetrievalTestOptions.defaults(),
                new RetrievalTrace.EmbeddingInfo(true, "llm_embedding", "llm_embedding|bge-m3|1024", 1024, 5L),
                List.of(),
                List.of(),
                List.of(hits),
                List.of(),
                12L);
    }

    private static RetrievalTrace.FusedHit fused(String chunkId, String contentHash) {
        return new RetrievalTrace.FusedHit(
                1,
                chunkId,
                "doc-" + chunkId,
                1L,
                "v1",
                "第一节",
                1,
                contentHash,
                "正文-" + chunkId,
                1,
                1,
                0.0164d,
                0.0164d,
                0.0328d,
                0.5d,
                0.6d,
                true,
                Map.of());
    }
}
