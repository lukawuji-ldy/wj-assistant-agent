package com.wuji.assistant.agent.kbtest;

import com.wuji.assistant.rag.diagnose.RetrievalTestOptions;
import com.wuji.assistant.rag.diagnose.RetrievalTrace;
import com.wuji.assistant.rag.vector.ClientRrfFusion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 请求内检索轨迹收集器。并发安全，不使用 {@code ThreadLocal}；由请求显式向下传递给领域检索。
 *
 * @author liudy
 */
public final class KbTestTraceCollector {

    /** 放在 {@code RunnableConfig} / {@code ToolContext} metadata 里的键 */
    public static final String META_KEY = "wuji.kbTestTraceCollector";

    private final RetrievalTestOptions options;
    private final List<DomainQueryTrace> traces = new CopyOnWriteArrayList<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();

    public KbTestTraceCollector(RetrievalTestOptions options) {
        this.options = options == null ? RetrievalTestOptions.defaults() : options;
    }

    public RetrievalTestOptions options() {
        return options;
    }

    /**
     * 记录一个领域、一个改写问句的检索轨迹。
     */
    public void record(String domain, String query, RetrievalTrace trace) {
        if (trace == null) {
            return;
        }
        traces.add(new DomainQueryTrace(domain, query, trace));
    }

    /**
     * 记录领域级失败，用于把状态降级为 {@link KbTestStatus#PARTIAL}。
     */
    public void fail(String domain, String message) {
        String text = message == null || message.isBlank() ? "领域执行失败" : message.trim();
        failures.add((domain == null || domain.isBlank() ? "" : domain.trim() + "：") + text);
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }

    /**
     * @return 首次出现顺序的领域名
     */
    public List<String> domains() {
        return List.copyOf(grouped().keySet());
    }

    /**
     * @return 按领域分组的完整轨迹
     */
    public List<DomainTrace> domainTraces() {
        List<DomainTrace> out = new ArrayList<>();
        for (Map.Entry<String, List<DomainQueryTrace>> entry : grouped().entrySet()) {
            List<DomainQueryTrace> group = List.copyOf(entry.getValue());
            out.add(new DomainTrace(entry.getKey(), group, groupFuse(group)));
        }
        return List.copyOf(out);
    }

    private Map<String, List<DomainQueryTrace>> grouped() {
        Map<String, List<DomainQueryTrace>> grouped = new LinkedHashMap<>();
        for (DomainQueryTrace trace : traces) {
            grouped.computeIfAbsent(trace.domain(), key -> new ArrayList<>()).add(trace);
        }
        return grouped;
    }

    /**
     * 多改写问句时，把各问句的融合结果当作一条 RRF 通路再融合一层。
     */
    private List<GroupFusedHit> groupFuse(List<DomainQueryTrace> group) {
        if (group == null || group.size() <= 1) {
            return List.of();
        }
        List<List<ClientRrfFusion.RankedDoc>> lanes = new ArrayList<>(group.size());
        Map<String, RetrievalTrace.FusedHit> firstHit = new LinkedHashMap<>();
        for (DomainQueryTrace item : group) {
            List<ClientRrfFusion.RankedDoc> lane = new ArrayList<>();
            for (RetrievalTrace.FusedHit hit : item.trace().fusedHits()) {
                if (hit == null || hit.chunkId() == null || hit.chunkId().isBlank()) {
                    continue;
                }
                lane.add(new ClientRrfFusion.RankedDoc(hit.chunkId(), hit.contentHash()));
                firstHit.putIfAbsent(hit.chunkId(), hit);
            }
            lanes.add(lane);
        }
        int rankConstant = options.rrfRankConstantOrDefault(RetrievalTestOptions.DEFAULT_RRF_RANK_CONSTANT);
        int topK = options.finalTopKOrDefault(RetrievalTestOptions.DEFAULT_FINAL_TOPK);
        List<ClientRrfFusion.DetailedFusedDoc> fused = ClientRrfFusion.fuseDetailed(lanes, rankConstant, topK);
        List<GroupFusedHit> out = new ArrayList<>(fused.size());
        int rank = 1;
        for (ClientRrfFusion.DetailedFusedDoc doc : fused) {
            RetrievalTrace.FusedHit hit = firstHit.get(doc.id());
            if (hit == null) {
                continue;
            }
            out.add(new GroupFusedHit(
                    rank++,
                    hit.chunkId(),
                    hit.docId(),
                    hit.content(),
                    doc.ranks(),
                    doc.contributions(),
                    doc.rrfScore(),
                    doc.normalizedScore(),
                    hit.businessScore()));
        }
        return List.copyOf(out);
    }
}
