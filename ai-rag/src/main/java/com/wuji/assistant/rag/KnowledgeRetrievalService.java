package com.wuji.assistant.rag;

import com.wuji.assistant.rag.diagnose.RetrievalTestOptions;
import com.wuji.assistant.rag.diagnose.RetrievalTrace;
import com.wuji.assistant.rag.vector.ParallelSearch;
import com.wuji.assistant.rag.vector.PgVectorScoreNormalizer;
import com.wuji.assistant.rag.vector.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * 知识库检索门面：委托 {@link VectorIndexPort}（PGVector 或 Elasticsearch）。
 *
 * @author liudy
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final VectorIndexPort vectorIndexPort;

    public KnowledgeRetrievalService(VectorIndexPort vectorIndexPort) {
        this.vectorIndexPort = vectorIndexPort;
    }

    public RetrievalResult retrieve(String query, int topK, double minReliableScore) {
        return vectorIndexPort.search(query, topK, minReliableScore);
    }

    /**
     * 在指定知识库命名空间内检索。
     *
     * @param collection 空则全库
     */
    public RetrievalResult retrieve(String query, int topK, double minReliableScore, String collection) {
        return vectorIndexPort.search(query, topK, minReliableScore, collection);
    }

    /**
     * 检索诊断：委托后端返回阈值过滤前的双路原始结果与 RRF 融合明细。
     * <p>
     * 与 {@link #retrieve(String, int, double, String)} 共享底层查询与融合函数，不改变线上语义。
     *
     * @param query     查询
     * @param collection 领域（直接模式必填）
     * @param options   临时参数覆盖；{@code null} 用后端默认
     */
    public RetrievalTrace diagnose(String query, String collection, RetrievalTestOptions options) {
        return vectorIndexPort.diagnose(query, collection, options);
    }

    /**
     * 多查询、多知识库并行检索后按 RRF 合并。collection 为空表示该查询不限库。
     * 单查询且不限库时直接走 {@link #retrieve(String, int, double)}。
     *
     * @param queries          检索问句
     * @param collections      选中的知识库；空表示不限库
     * @param topK             每路与合并后的条数
     * @param minReliableScore 合并后的最低分
     * @param rankConstant     RRF rank_constant
     * @param executor         并行执行器
     */
    public RetrievalResult retrieveMany(List<String> queries,
                                        List<String> collections,
                                        int topK,
                                        double minReliableScore,
                                        int rankConstant,
                                        Executor executor) {
        List<String> asked = normalize(queries);
        if (asked.isEmpty()) {
            return new RetrievalResult(List.of(), true, "查询为空");
        }
        List<String> scopes = normalize(collections);
        if (asked.size() == 1 && scopes.isEmpty()) {
            return retrieve(asked.get(0), topK, minReliableScore);
        }
        List<Callable<List<RetrievalResult.Hit>>> tasks = new ArrayList<>();
        if (scopes.isEmpty()) {
            for (String query : asked) {
                tasks.add(() -> hitsOf(query, topK, minReliableScore, null));
            }
        } else {
            for (String query : asked) {
                for (String collection : scopes) {
                    tasks.add(() -> hitsOf(query, topK, minReliableScore, collection));
                }
            }
        }
        List<List<RetrievalResult.Hit>> groups = ParallelSearch.joinAll(tasks, executor);
        List<RetrievalResult.Hit> fused = PgVectorScoreNormalizer.fuseRankedLists(groups, Math.max(1, topK), rankConstant)
                .stream()
                .filter(hit -> hit.score() >= minReliableScore)
                .toList();
        if (fused.isEmpty()) {
            return new RetrievalResult(List.of(), true, "无可靠知识命中");
        }
        return new RetrievalResult(fused, false, null);
    }

    private List<RetrievalResult.Hit> hitsOf(String query, int topK, double minReliableScore, String collection) {
        try {
            RetrievalResult result = StringUtils.hasText(collection)
                    ? vectorIndexPort.search(query, topK, minReliableScore, collection)
                    : vectorIndexPort.search(query, topK, minReliableScore);
            if (result == null || result.hits() == null) {
                return List.of();
            }
            return result.hits();
        } catch (RuntimeException e) {
            log.warn("knowledge retrieve failed collection={}: {}", collection, e.toString());
            return List.of();
        }
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String trimmed = value.trim();
            if (!out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /**
     * 合并余弦与关键词命中（PG 适配器单元测试用）。
     */
    static java.util.List<RetrievalResult.Hit> mergeHits(
            java.util.List<RetrievalResult.Hit> cosineHits,
            java.util.List<RetrievalResult.Hit> keywordHits,
            int limit) {
        return com.wuji.assistant.rag.vector.PgVectorIndexAdapter.mergeHits(cosineHits, keywordHits, limit);
    }

    /**
     * ILIKE 模式（PG 适配器单元测试用）。
     */
    static java.util.List<String> likePatterns(String query) {
        return com.wuji.assistant.rag.vector.PgVectorIndexAdapter.likePatterns(query);
    }
}
