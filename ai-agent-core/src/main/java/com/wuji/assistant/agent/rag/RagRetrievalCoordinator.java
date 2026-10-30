package com.wuji.assistant.agent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.agent.config.WujiRagProperties;
import com.wuji.assistant.rag.KnowledgeRetrievalService;
import com.wuji.assistant.rag.RetrievalResult;
import com.wuji.assistant.rag.config.RagVectorProperties;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import com.wuji.assistant.rag.router.KbCollection;
import com.wuji.assistant.rag.router.KbCollectionRepository;
import com.wuji.assistant.rag.router.KnowledgeRouter;
import com.wuji.assistant.rag.vector.PgVectorScoreNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 聊天检索入口：可选查询改写、按知识库说明选库，再并行检索。
 * 路由或改写关闭时与直接调用 {@link KnowledgeRetrievalService#retrieve} 相同。
 *
 * @author liudy
 */
@Service
public class RagRetrievalCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalCoordinator.class);

    private final KnowledgeRetrievalService retrievalService;
    private final KbCollectionRepository collections;
    private final EmbeddingClient embeddingClient;
    private final RagQueryRewriteOrchestrator queryRewriteOrchestrator;
    private final WujiRagProperties ragProperties;
    private final RagVectorProperties vectorProperties;
    private final Scheduler kbIngestScheduler;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, float[]> descriptionVectors = new ConcurrentHashMap<>();

    public RagRetrievalCoordinator(KnowledgeRetrievalService retrievalService,
                                   KbCollectionRepository collections,
                                   ObjectProvider<EmbeddingClient> embeddingClients,
                                   RagQueryRewriteOrchestrator queryRewriteOrchestrator,
                                   WujiRagProperties ragProperties,
                                   RagVectorProperties vectorProperties,
                                   @Qualifier("kbIngestScheduler") Scheduler kbIngestScheduler,
                                   ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.collections = collections;
        this.embeddingClient = embeddingClients.getIfAvailable();
        this.queryRewriteOrchestrator = queryRewriteOrchestrator;
        this.ragProperties = ragProperties;
        this.vectorProperties = vectorProperties;
        this.kbIngestScheduler = kbIngestScheduler;
        this.objectMapper = objectMapper;
    }

    /**
     * @param userId         用户
     * @param conversationId 会话
     * @param query          原问题
     * @param topK           条数
     * @param minScore       可靠分
     * @param fetchHits      false 时只改写，不查库
     */
    public Outcome retrieve(String userId,
                            String conversationId,
                            String query,
                            int topK,
                            double minScore,
                            boolean fetchHits) {
        RagQueryRewrite rewritten = queryRewriteOrchestrator.rewrite(userId, conversationId, query);
        String hint = rewritten.relational() ? queryRewriteOrchestrator.relationHint() : "";
        if (!fetchHits) {
            return new Outcome(new RetrievalResult(List.of(), true, "未预取"), rewritten.relational(), hint);
        }
        List<String> queries = rewritten.queries().isEmpty() && StringUtils.hasText(query)
                ? List.of(query.trim())
                : rewritten.queries();
        String routeQuery = queries.isEmpty() ? query : queries.get(0);
        List<String> selected = route(routeQuery);
        RetrievalResult result;
        if (queries.size() <= 1 && selected.isEmpty()) {
            String single = queries.isEmpty() ? "" : queries.get(0);
            result = retrievalService.retrieve(single, topK, minScore);
        } else {
            result = retrievalService.retrieveMany(
                    queries, selected, topK, minScore, rankConstant(), executor());
        }
        return new Outcome(result, rewritten.relational(), hint);
    }

    /**
     * 工具返回 JSON。关系类问题带上 relationHint。
     */
    public String retrieveJson(String query, int topK, double minScore) {
        Outcome outcome = retrieve(null, null, query, topK, minScore, true);
        RetrievalResult result = outcome.result();
        boolean rejected = result == null || result.rejected();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rejected", rejected);
        if (rejected) {
            String reason = result == null || result.rejectReason() == null
                    ? "无可靠知识命中" : result.rejectReason();
            body.put("rejectReason", reason);
        }
        body.put("hits", result == null || result.hits() == null ? List.of() : result.hits());
        if (outcome.relational()) {
            body.put("relational", true);
            if (StringUtils.hasText(outcome.relationHint())) {
                body.put("relationHint", outcome.relationHint());
            }
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return "{\"rejected\":true,\"rejectReason\":\"serialize_failed\"}";
        }
    }

    private List<String> route(String query) {
        WujiRagProperties.Router router = ragProperties.getRouter();
        if (router == null || !router.isEnabled() || !StringUtils.hasText(query)) {
            return List.of();
        }
        if (embeddingClient == null || !embeddingClient.available()) {
            log.info("kb router skipped: embedding unavailable");
            return List.of();
        }
        float[] queryVector;
        try {
            queryVector = embeddingClient.embed(query);
        } catch (RuntimeException e) {
            log.warn("kb router embed query failed: {}", e.toString());
            return List.of();
        }
        List<KbCollection> rows;
        try {
            rows = collections.listAll();
        } catch (RuntimeException e) {
            log.warn("kb router catalog failed: {}", e.toString());
            return List.of();
        }
        List<KnowledgeRouter.Profile> profiles = new ArrayList<>();
        for (KbCollection row : rows) {
            if (row == null || !StringUtils.hasText(row.collection()) || !StringUtils.hasText(row.description())) {
                continue;
            }
            float[] vector = embedDescription(row.collection().trim(), row.description());
            profiles.add(new KnowledgeRouter.Profile(row.collection().trim(), vector));
        }
        List<KnowledgeRouter.Routed> selected = KnowledgeRouter.select(
                queryVector, profiles, router.getTopN(), router.getMinScore());
        if (selected.isEmpty()) {
            log.info("kb router selected none, fallback unscoped");
            return List.of();
        }
        log.info("kb router selected {}", selected.stream().map(KnowledgeRouter.Routed::collection).toList());
        return selected.stream().map(KnowledgeRouter.Routed::collection).toList();
    }

    private float[] embedDescription(String collection, String description) {
        String key = collection + "\n" + description;
        float[] cached = descriptionVectors.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            float[] vector = embeddingClient.embed(description);
            if (vector != null) {
                if (descriptionVectors.size() > 256) {
                    descriptionVectors.clear();
                }
                descriptionVectors.put(key, vector);
            }
            return vector;
        } catch (RuntimeException e) {
            log.warn("kb router embed description failed collection={}: {}", collection, e.toString());
            return null;
        }
    }

    private int rankConstant() {
        if (vectorProperties == null || vectorProperties.getPgvector() == null) {
            return PgVectorScoreNormalizer.DEFAULT_RANK_CONSTANT;
        }
        return vectorProperties.getPgvector().getRrfRankConstant();
    }

    private Executor executor() {
        if (kbIngestScheduler == null) {
            return null;
        }
        return command -> kbIngestScheduler.schedule(command);
    }

    /**
     * @param result       检索结果；未取片段时 rejected
     * @param relational   关系类问法
     * @param relationHint 结构化回答说明，可空
     */
    public record Outcome(RetrievalResult result, boolean relational, String relationHint) {
    }
}
