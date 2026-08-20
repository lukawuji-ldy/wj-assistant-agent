package com.wuji.assistant.rag.vector.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.RetrievalResult;
import com.wuji.assistant.rag.config.RagVectorProperties;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import com.wuji.assistant.rag.vector.ClientRrfFusion;
import com.wuji.assistant.rag.vector.VectorChunkDocument;
import com.wuji.assistant.rag.vector.VectorIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Elasticsearch Hybrid Search 适配器：原生 Client 写入 + 客户端 RRF（BM25∪kNN）+ PG 水合。
 * <p>
 * 不使用集群 {@code retriever.rrf}（需 Enterprise 许可证），在应用侧按相同公式融合，
 * Basic 许可证可用且效果对齐原生 RRF。
 *
 * @author liudy
 */
@Component
@ConditionalOnClass(ElasticsearchClient.class)
@ConditionalOnProperty(name = "wuji.rag.vector-backend", havingValue = "elasticsearch")
public class ElasticsearchIndexAdapter implements VectorIndexPort {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexAdapter.class);

    private final ElasticsearchClient client;
    private final JdbcTemplate jdbcTemplate;
    private final RagVectorProperties properties;
    private final EmbeddingClient embeddingClient;
    private final ElasticsearchIndexManager indexManager;
    private final ObjectMapper objectMapper;

    public ElasticsearchIndexAdapter(ElasticsearchClient client,
                                       JdbcTemplate jdbcTemplate,
                                       RagVectorProperties properties,
                                       ObjectProvider<EmbeddingClient> embeddingClients,
                                       ElasticsearchIndexManager indexManager,
                                       ObjectMapper objectMapper) {
        this.client = client;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.embeddingClient = embeddingClients.getIfAvailable(this::unavailableClient);
        this.indexManager = indexManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void upsertChunk(VectorChunkDocument document) {
        indexManager.ensureIndexBeforeWrite();
        String indexName = indexName();
        try {
            Map<String, Object> source = toSource(document);
            client.index(IndexRequest.of(i -> i
                    .index(indexName)
                    .id(document.chunkId().toString())
                    .document(source)));
        } catch (IOException e) {
            throw ragUnavailable("upsert chunk failed: " + document.chunkId(), e);
        }
    }

    @Override
    public void deprecateChunk(UUID chunkId) {
        String indexName = indexName();
        try {
            Map<String, Object> patch = Map.of(
                    "status", "DEPRECATED",
                    "version_status", "DEPRECATED");
            client.update(UpdateRequest.of(u -> u
                    .index(indexName)
                    .id(chunkId.toString())
                    .doc(patch)
                    .docAsUpsert(false)), Map.class);
        } catch (IOException e) {
            log.warn("deprecateChunk ES failed chunkId={}: {}", chunkId, e.toString());
        }
    }

    @Override
    public void deprecateVersion(long versionId) {
        String indexName = indexName();
        try {
            client.updateByQuery(UpdateByQueryRequest.of(u -> u
                    .index(indexName)
                    .query(q -> q.term(t -> t.field("version_id").value(String.valueOf(versionId))))
                    .script(s -> s.source("""
                                    ctx._source.status = 'DEPRECATED';
                                    ctx._source.version_status = 'DEPRECATED';
                                    """))));
        } catch (IOException e) {
            log.warn("deprecateVersion ES failed versionId={}: {}", versionId, e.toString());
        }
    }

    @Override
    public RetrievalResult search(String query, int topK, double minReliableScore) {
        if (!StringUtils.hasText(query)) {
            return new RetrievalResult(List.of(), true, "查询为空");
        }
        int limit = Math.max(1, topK);
        RagVectorProperties.Hybrid hybrid = properties.getElasticsearch().getHybrid();
        float[] queryVector = embeddingClient.available() ? embeddingClient.embed(query.trim()) : null;
        boolean hybridEnabled = hybrid.isEnabled() && queryVector != null;

        try {
            List<ScoredChunkId> candidates;
            if (hybridEnabled) {
                assertQueryVectorMatchesIndex(queryVector);
                candidates = searchHybridClientRrf(query.trim(), queryVector, hybrid, limit);
            } else {
                SearchResponse<Map> response = client.search(buildBm25Search(limit, query.trim()), Map.class);
                candidates = extractBm25OnlyCandidates(response);
            }
            List<RetrievalResult.Hit> hits = hydrateHits(candidates, minReliableScore);
            if (hits.isEmpty()) {
                return new RetrievalResult(List.of(), true, "无可靠知识命中");
            }
            return new RetrievalResult(hits, false, null);
        } catch (IOException e) {
            throw ragUnavailable("hybrid search failed: " + rootCauseDetail(e), e);
        } catch (RuntimeException e) {
            if (e instanceof WujiException) {
                throw e;
            }
            if (isEsClientFailure(e)) {
                throw ragUnavailable("hybrid search failed: " + rootCauseDetail(e), e);
            }
            throw e;
        }
    }

    /**
     * 查询向量维度须与 ES mapping 一致；否则 knn 会报 all shards failed（难读）。
     */
    private void assertQueryVectorMatchesIndex(float[] queryVector) {
        var indexDims = indexManager.readIndexEmbeddingDimensions();
        if (indexDims.isEmpty()) {
            return;
        }
        int mapped = indexDims.getAsInt();
        if (queryVector.length == mapped) {
            return;
        }
        throw ragUnavailable("""
                查询向量维度 %d 与 Elasticsearch 索引 %s 的 embedding dims=%d 不一致。
                常见原因：llm_config.extra_json.dimensions 与模型实际输出不符，或索引未按新维度重建。
                请修正 dimensions 后重启（recreate-index-on-dimension-mismatch=true 时会删建索引），并对 ACTIVE 版本重建向量。
                """.formatted(queryVector.length, indexName(), mapped).trim(), null);
    }

    /**
     * 双路独立查询 + 客户端 RRF，对齐 ES 原生公式，不依赖 RRF 许可证。
     */
    private List<ScoredChunkId> searchHybridClientRrf(String query, float[] queryVector,
                                                      RagVectorProperties.Hybrid hybrid, int limit)
            throws IOException {
        int bm25Size = Math.max(limit, hybrid.getBm25Size());
        int knnSize = Math.max(limit, hybrid.getKnnSize());
        SearchResponse<Map> bm25Response = client.search(buildBm25Search(bm25Size, query), Map.class);
        SearchResponse<Map> knnResponse = client.search(
                buildKnnSearch(knnSize, queryVector, hybrid), Map.class);
        List<ClientRrfFusion.RankedDoc> bm25 = toRankedDocs(bm25Response);
        List<ClientRrfFusion.RankedDoc> knn = toRankedDocs(knnResponse);
        List<ClientRrfFusion.FusedDoc> fused = ClientRrfFusion.fuse(
                List.of(bm25, knn), hybrid.getRrfRankConstant(), limit);
        List<ScoredChunkId> out = new ArrayList<>(fused.size());
        for (ClientRrfFusion.FusedDoc doc : fused) {
            out.add(new ScoredChunkId(doc.id(), doc.normalizedScore(), doc.contentHash()));
        }
        return out;
    }

    @Override
    public int embeddedCount(long versionId) {
        String indexName = indexName();
        try {
            var response = client.count(CountRequest.of(c -> c
                    .index(indexName)
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.term(t -> t.field("version_id").value(String.valueOf(versionId))))
                            .filter(f -> f.term(t -> t.field("status").value("ACTIVE")))
                            .filter(f -> f.term(t -> t.field("version_status").value("ACTIVE")))))));
            return (int) response.count();
        } catch (IOException e) {
            throw ragUnavailable("embeddedCount failed", e);
        }
    }

    @Override
    public String vectorBackend() {
        return "elasticsearch";
    }

    @Override
    public String indexName() {
        return properties.getElasticsearch().getIndexName();
    }

    private SearchRequest buildBm25Search(int limit, String query) throws JsonProcessingException {
        String json = """
                {
                  "size": %d,
                  "query": {
                    "bool": {
                      "must": [{ "match": { "content": %s } }],
                      "filter": [
                        { "term": { "status": "ACTIVE" } },
                        { "term": { "version_status": "ACTIVE" } }
                      ]
                    }
                  }
                }
                """.formatted(limit, objectMapper.writeValueAsString(query));
        return SearchRequest.of(s -> s.index(indexName()).withJson(new StringReader(json)));
    }

    private SearchRequest buildKnnSearch(int knnSize, float[] queryVector,
                                         RagVectorProperties.Hybrid hybrid) throws JsonProcessingException {
        int k = Math.max(1, knnSize);
        int numCandidates = Math.max(hybrid.getKnnSize() * 5, Math.max(k * 5, 100));
        String json = """
                {
                  "size": %d,
                  "knn": {
                    "field": "embedding",
                    "query_vector": %s,
                    "k": %d,
                    "num_candidates": %d,
                    "filter": [
                      { "term": { "status": "ACTIVE" } },
                      { "term": { "version_status": "ACTIVE" } }
                    ]
                  }
                }
                """.formatted(k, vectorJson(queryVector), k, numCandidates);
        return SearchRequest.of(s -> s.index(indexName()).withJson(new StringReader(json)));
    }

    private List<ClientRrfFusion.RankedDoc> toRankedDocs(SearchResponse<Map> response) {
        List<ClientRrfFusion.RankedDoc> out = new ArrayList<>();
        if (response.hits() == null || response.hits().hits() == null) {
            return out;
        }
        for (Hit<Map> hit : response.hits().hits()) {
            String chunkId = resolveChunkId(hit);
            if (!StringUtils.hasText(chunkId)) {
                continue;
            }
            out.add(new ClientRrfFusion.RankedDoc(chunkId, resolveContentHash(hit)));
        }
        return out;
    }

    private List<ScoredChunkId> extractBm25OnlyCandidates(SearchResponse<Map> response) {
        List<ScoredChunkId> out = new ArrayList<>();
        if (response.hits() == null || response.hits().hits() == null) {
            return out;
        }
        for (Hit<Map> hit : response.hits().hits()) {
            String chunkId = resolveChunkId(hit);
            if (!StringUtils.hasText(chunkId)) {
                continue;
            }
            out.add(new ScoredChunkId(chunkId, 1.0, resolveContentHash(hit)));
        }
        return out;
    }

    private static String resolveChunkId(Hit<Map> hit) {
        String chunkId = hit.id();
        if (!StringUtils.hasText(chunkId) && hit.source() != null) {
            Object cid = hit.source().get("chunk_id");
            chunkId = cid == null ? null : String.valueOf(cid);
        }
        return chunkId;
    }

    private static String resolveContentHash(Hit<Map> hit) {
        if (hit.source() == null || hit.source().get("content_hash") == null) {
            return null;
        }
        return String.valueOf(hit.source().get("content_hash"));
    }

    private static boolean isEsClientFailure(RuntimeException e) {
        String name = e.getClass().getName();
        return name.contains("ElasticsearchException") || name.contains("elasticsearch");
    }

    private List<RetrievalResult.Hit> hydrateHits(List<ScoredChunkId> candidates, double minReliableScore) {
        List<RetrievalResult.Hit> hits = new ArrayList<>();
        for (ScoredChunkId candidate : candidates) {
            if (candidate.score() < minReliableScore) {
                continue;
            }
            RetrievalResult.Hit hit = hydrateOne(candidate);
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private RetrievalResult.Hit hydrateOne(ScoredChunkId candidate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT c.chunk_id::text AS chunk_id,
                       r.content AS content,
                       c.current_revision AS revision,
                       r.content_hash AS content_hash,
                       c.doc_id, c.collection, c.section, c.summary, c.chunk_key,
                       c.version_id, v.version, v.embedding_config_id,
                       v.embedding_model_version
                FROM kb_chunk c
                JOIN kb_document_version v ON v.id = c.version_id AND v.status = 'ACTIVE'
                JOIN kb_chunk_revision r
                  ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision AND r.status = 'ACTIVE'
                WHERE c.status = 'ACTIVE' AND c.chunk_id = ?::uuid
                """, candidate.chunkId());
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String pgHash = String.valueOf(row.get("content_hash"));
        if (StringUtils.hasText(candidate.esContentHash()) && !candidate.esContentHash().equals(pgHash)) {
            log.debug("discard drift hit chunkId={} esHash={} pgHash={}",
                    candidate.chunkId(), candidate.esContentHash(), pgHash);
            return null;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("doc_id", row.get("doc_id"));
        meta.put("collection", row.get("collection"));
        meta.put("section", row.get("section"));
        meta.put("summary", row.get("summary"));
        meta.put("chunk_key", row.get("chunk_key"));
        meta.put("version_id", row.get("version_id"));
        meta.put("version", row.get("version"));
        meta.put("revision", row.get("revision"));
        meta.put("content_hash", pgHash);
        meta.put("status", "ACTIVE");
        Object configId = row.get("embedding_config_id");
        if (configId != null && StringUtils.hasText(String.valueOf(configId))) {
            meta.put("embedding_config_id", String.valueOf(configId));
        }
        Object modelVersion = row.get("embedding_model_version");
        if (modelVersion != null && StringUtils.hasText(String.valueOf(modelVersion))) {
            meta.put("embedding_model_version", String.valueOf(modelVersion));
        }
        String content = row.get("content") == null ? "" : String.valueOf(row.get("content"));
        return new RetrievalResult.Hit(candidate.chunkId(), content, candidate.score(), meta);
    }

    private static Map<String, Object> toSource(VectorChunkDocument document) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("content", document.content() == null ? "" : document.content());
        source.put("chunk_id", document.chunkId().toString());
        source.put("doc_id", document.docId());
        source.put("version_id", String.valueOf(document.versionId()));
        source.put("collection", document.collection());
        source.put("status", document.status());
        source.put("version_status", document.versionStatus());
        source.put("revision", String.valueOf(document.revision()));
        source.put("content_hash", document.contentHash());
        source.put("section", document.section() == null ? "" : document.section());
        source.put("summary", document.summary() == null ? "" : document.summary());
        if (document.embedding() != null) {
            source.put("embedding", toFloatList(document.embedding()));
        }
        return source;
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private static String vectorJson(float[] vector) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(toFloatList(vector));
    }

    private static WujiException ragUnavailable(String detail, Exception cause) {
        return new WujiException(ErrorCode.RAG_UNAVAILABLE, detail, cause);
    }

    /** 从 ES 异常链中提取可读 root cause（如维度不一致）。 */
    static String rootCauseDetail(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        Throwable cursor = error;
        String best = error.getMessage();
        while (cursor != null) {
            String msg = cursor.getMessage();
            if (StringUtils.hasText(msg)) {
                best = msg;
                String lower = msg.toLowerCase();
                if (lower.contains("dimensions") || lower.contains("license") || lower.contains("parse")) {
                    return msg;
                }
            }
            cursor = cursor.getCause();
        }
        return StringUtils.hasText(best) ? best : error.getClass().getSimpleName();
    }

    private EmbeddingClient unavailableClient() {
        return new EmbeddingClient() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public float[] embed(String text) {
                return null;
            }
        };
    }

    private record ScoredChunkId(String chunkId, double score, String esContentHash) {
    }
}
