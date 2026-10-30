package com.wuji.assistant.rag.vector;

import com.wuji.assistant.rag.RetrievalQueryTerms;
import com.wuji.assistant.rag.RetrievalResult;
import com.wuji.assistant.rag.config.RagVectorProperties;
import com.wuji.assistant.rag.diagnose.DiagnoseSupport;
import com.wuji.assistant.rag.diagnose.RetrievalTestOptions;
import com.wuji.assistant.rag.diagnose.RetrievalTrace;
import com.wuji.assistant.rag.ingest.DocumentIngestService;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PGVector 适配器：余弦 + ILIKE 检索；向量写入 kb_chunk.embedding。
 *
 * @author liudy
 */
@Component
@ConditionalOnProperty(name = "wuji.rag.vector-backend", havingValue = "pgvector", matchIfMissing = true)
public class PgVectorIndexAdapter implements VectorIndexPort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorIndexAdapter.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;
    private final RagVectorProperties properties;

    public PgVectorIndexAdapter(JdbcTemplate jdbcTemplate,
                                ObjectProvider<EmbeddingClient> embeddingClients,
                                RagVectorProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClients.getIfAvailable(() -> unavailableClient());
        this.properties = properties;
    }

    @Override
    public void upsertChunk(VectorChunkDocument document) {
        if (!embeddingClient.available() || document.embedding() == null) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        String literal = DocumentIngestService.toVectorLiteral(document.embedding());
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding = ?::vector,
                    embedding_content_revision = ?,
                    embedding_content_hash = ?,
                    update_time = ?
                WHERE chunk_id = ?
                """,
                literal,
                document.revision(),
                document.contentHash(),
                now,
                document.chunkId());
    }

    @Override
    public void deprecateChunk(UUID chunkId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding = NULL, embedding_content_revision = NULL,
                    embedding_content_hash = NULL, update_time = ?
                WHERE chunk_id = ?
                """, now, chunkId);
    }

    @Override
    public void deprecateVersion(long versionId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE kb_chunk
                SET embedding = NULL, embedding_content_revision = NULL,
                    embedding_content_hash = NULL, update_time = ?
                WHERE version_id = ? AND status = 'DEPRECATED'
                """, now, versionId);
    }

    @Override
    public RetrievalResult search(String query, int topK, double minReliableScore) {
        return search(query, topK, minReliableScore, null);
    }

    @Override
    public RetrievalResult search(String query, int topK, double minReliableScore, String collection) {
        if (!StringUtils.hasText(query)) {
            return new RetrievalResult(List.of(), true, "查询为空");
        }
        int limit = Math.max(1, topK);
        String q = query.trim();
        String scope = StringUtils.hasText(collection) ? collection.trim() : null;
        List<RetrievalResult.Hit> cosineHits = List.of();
        if (embeddingClient.available()) {
            cosineHits = cosineSearch(q, limit, scope);
        } else {
            log.debug("embedding unavailable, keyword-only retrieval");
        }
        List<RetrievalResult.Hit> keywordHits = keywordSearch(q, limit, scope);
        int rankConstant = properties == null || properties.getPgvector() == null
                ? PgVectorScoreNormalizer.DEFAULT_RANK_CONSTANT
                : properties.getPgvector().getRrfRankConstant();
        List<RetrievalResult.Hit> reliable = mergeHits(cosineHits, keywordHits, limit, rankConstant).stream()
                .filter(h -> h.score() >= minReliableScore)
                .toList();
        if (reliable.isEmpty()) {
            return new RetrievalResult(List.of(), true, "无可靠知识命中");
        }
        return new RetrievalResult(reliable, false, null);
    }

    @Override
    public RetrievalTrace diagnose(String query, String domain, RetrievalTestOptions options) {
        long start = System.currentTimeMillis();
        RetrievalTestOptions effective = (options == null ? RetrievalTestOptions.defaults() : options).validate();
        String q = query == null ? "" : query.trim();
        String scope = StringUtils.hasText(domain) ? domain.trim() : null;
        int vectorTopK = effective.vectorTopKOrDefault(RetrievalTestOptions.DEFAULT_VECTOR_TOPK);
        int keywordTopK = effective.keywordTopKOrDefault(RetrievalTestOptions.DEFAULT_KEYWORD_TOPK);
        int finalTopK = Math.max(1, effective.finalTopKOrDefault(RetrievalTestOptions.DEFAULT_FINAL_TOPK));
        int rankConstant = effective.rrfRankConstantOrDefault(defaultRankConstant());
        double minReliable = effective.minReliableScoreOrDefault(RetrievalTestOptions.DEFAULT_MIN_RELIABLE_SCORE);

        boolean available = embeddingClient.available();
        long embedStart = System.currentTimeMillis();
        float[] vector = available && StringUtils.hasText(q) ? embeddingClient.embed(q) : null;
        long embedMs = System.currentTimeMillis() - embedStart;

        List<String> warnings = new ArrayList<>();
        List<RetrievalResult.Hit> cosineHits = List.of();
        if (vector != null) {
            cosineHits = cosineSearchByVector(q, DocumentIngestService.toVectorLiteral(vector), vectorTopK, scope);
        } else if (available) {
            warnings.add("embedding 返回空向量，已降级为 ILIKE 检索");
        } else {
            warnings.add("embedding 不可用，已降级为 ILIKE 检索");
        }
        List<RetrievalResult.Hit> keywordHits = StringUtils.hasText(q) ? keywordSearch(q, keywordTopK, scope) : List.of();

        List<RetrievalResult.Hit> cosine = DiagnoseSupport.dedupe(cosineHits);
        List<RetrievalResult.Hit> keyword = DiagnoseSupport.dedupe(keywordHits);
        double[] keywordScores = new double[keyword.size()];
        for (int i = 0; i < keyword.size(); i++) {
            keywordScores[i] = PgVectorScoreNormalizer.keywordRankScore(i, keyword.size());
        }

        List<RetrievalResult.Hit> fused = PgVectorScoreNormalizer.fuse(cosineHits, keywordHits, finalTopK, rankConstant);
        List<ClientRrfFusion.DetailedFusedDoc> detail = ClientRrfFusion.fuseDetailed(
                List.of(toRankedDocs(cosine), toRankedDocs(keyword)), rankConstant, finalTopK);

        Map<String, Integer> vectorRank = new LinkedHashMap<>();
        Map<String, Integer> keywordRank = new LinkedHashMap<>();
        Map<String, double[]> contributions = new LinkedHashMap<>();
        for (ClientRrfFusion.DetailedFusedDoc doc : detail) {
            List<Integer> ranks = doc.ranks();
            List<Double> contrib = doc.contributions();
            vectorRank.put(doc.id(), ranks.get(0));
            keywordRank.put(doc.id(), ranks.get(1));
            contributions.put(doc.id(), new double[]{contrib.get(0), contrib.get(1)});
        }

        RetrievalTrace.EmbeddingInfo embedding = new RetrievalTrace.EmbeddingInfo(
                available,
                embeddingClient.embeddingConfigId(),
                embeddingClient.embeddingModelVersion(),
                embeddingClient.dimensions(),
                embedMs);
        return new RetrievalTrace(
                "pgvector",
                scope,
                q,
                new RetrievalTestOptions(vectorTopK, keywordTopK, finalTopK, minReliable, rankConstant),
                embedding,
                DiagnoseSupport.toHits(cosine, null),
                DiagnoseSupport.toHits(keyword, keywordScores),
                DiagnoseSupport.fuseHits(fused, vectorRank, keywordRank, contributions, minReliable),
                List.copyOf(warnings),
                System.currentTimeMillis() - start);
    }

    private int defaultRankConstant() {
        return properties == null || properties.getPgvector() == null
                ? PgVectorScoreNormalizer.DEFAULT_RANK_CONSTANT
                : properties.getPgvector().getRrfRankConstant();
    }

    private static List<ClientRrfFusion.RankedDoc> toRankedDocs(List<RetrievalResult.Hit> hits) {
        List<ClientRrfFusion.RankedDoc> out = new ArrayList<>();
        if (hits == null) {
            return out;
        }
        for (RetrievalResult.Hit hit : hits) {
            out.add(new ClientRrfFusion.RankedDoc(hit.chunkId(), DiagnoseSupport.metaOf(hit, "content_hash") == null
                    ? null : String.valueOf(DiagnoseSupport.metaOf(hit, "content_hash"))));
        }
        return out;
    }

    @Override
    public int embeddedCount(long versionId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM kb_chunk
                WHERE version_id = ? AND status = 'ACTIVE' AND embedding IS NOT NULL
                """, Integer.class, versionId);
        return count == null ? 0 : count;
    }

    @Override
    public String vectorBackend() {
        return "pgvector";
    }

    @Override
    public String indexName() {
        return null;
    }

    public static List<RetrievalResult.Hit> mergeHits(List<RetrievalResult.Hit> cosineHits,
                                               List<RetrievalResult.Hit> keywordHits,
                                               int limit) {
        return mergeHits(cosineHits, keywordHits, limit, PgVectorScoreNormalizer.DEFAULT_RANK_CONSTANT);
    }

    public static List<RetrievalResult.Hit> mergeHits(List<RetrievalResult.Hit> cosineHits,
                                               List<RetrievalResult.Hit> keywordHits,
                                               int limit,
                                               int rankConstant) {
        return PgVectorScoreNormalizer.fuse(cosineHits, keywordHits, limit, rankConstant);
    }

    public static List<String> likePatterns(String query) {
        LinkedHashSet<String> patterns = new LinkedHashSet<>();
        String full = RetrievalQueryTerms.likeLiteral(query);
        if (full.length() >= 2) {
            patterns.add("%" + full + "%");
        }
        for (String term : RetrievalQueryTerms.terms(query)) {
            String lit = RetrievalQueryTerms.likeLiteral(term);
            if (lit.length() >= 2) {
                patterns.add("%" + lit + "%");
            }
        }
        return new ArrayList<>(patterns);
    }

    private List<RetrievalResult.Hit> cosineSearch(String query, int limit, String collection) {
        float[] vector = embeddingClient.embed(query);
        if (vector == null) {
            return keywordSearch(query, limit, collection);
        }
        return cosineSearchByVector(query, DocumentIngestService.toVectorLiteral(vector), limit, collection);
    }

    private List<RetrievalResult.Hit> cosineSearchByVector(String query, String literal, int limit, String collection) {
        boolean scoped = StringUtils.hasText(collection);
        String sql = """
                SELECT c.chunk_id::text AS chunk_id,
                       r.content AS content,
                       c.current_revision AS revision,
                       r.content_hash AS content_hash,
                       c.doc_id, c.collection, c.section, c.summary, c.chunk_key,
                       c.version_id, v.version, v.embedding_config_id,
                       v.embedding_model_version,
                       (1 - (c.embedding <=> ?::vector)) AS score
                FROM kb_chunk c
                JOIN kb_document_version v ON v.id = c.version_id AND v.status = 'ACTIVE'
                JOIN kb_chunk_revision r
                  ON r.chunk_id = c.chunk_id AND r.revision = c.current_revision AND r.status = 'ACTIVE'
                WHERE c.status = 'ACTIVE'
                  AND c.embedding IS NOT NULL
                """
                + (scoped ? " AND c.domain_name = ? " : "")
                + """
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
                """;
        List<Object> args = new ArrayList<>();
        args.add(literal);
        if (scoped) {
            args.add(collection.trim());
        }
        args.add(literal);
        args.add(limit);
        List<RetrievalResult.Hit> hits = new ArrayList<>();
        try {
            jdbcTemplate.query(sql,
                    rs -> {
                        while (rs.next()) {
                            hits.add(toHit(
                                    rs.getString("chunk_id"),
                                    rs.getString("content"),
                                    rs.getDouble("score"),
                                    rs));
                        }
                        return null;
                    },
                    args.toArray());
        } catch (Exception e) {
            log.warn("cosine retrieve failed, fallback ILIKE: {}", e.toString());
            return keywordSearch(query, limit, collection);
        }
        return hits;
    }

    private List<RetrievalResult.Hit> keywordSearch(String query, int limit, String collection) {
        List<String> patterns = likePatterns(query);
        if (patterns.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
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
                WHERE c.status = 'ACTIVE' AND (
                """);
        for (int i = 0; i < patterns.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("r.content ILIKE ?");
        }
        sql.append(") ");
        if (StringUtils.hasText(collection)) {
            sql.append(" AND c.domain_name = ? ");
        }
        sql.append(" ORDER BY length(r.content) ASC LIMIT ?");
        List<Object> args = new ArrayList<>(patterns);
        if (StringUtils.hasText(collection)) {
            args.add(collection.trim());
        }
        args.add(limit);
        List<RetrievalResult.Hit> hits = new ArrayList<>();
        try {
            jdbcTemplate.query(sql.toString(),
                    rs -> {
                        while (rs.next()) {
                            hits.add(toHit(
                                    rs.getString("chunk_id"),
                                    rs.getString("content"),
                                    1.0,
                                    rs));
                        }
                        return null;
                    },
                    args.toArray());
        } catch (Exception e) {
            log.warn("knowledge retrieve failed: {}", e.toString());
            return List.of();
        }
        return hits;
    }

    private RetrievalResult.Hit toHit(String chunkId, String content, double score,
                                      java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("doc_id", rs.getString("doc_id"));
        meta.put("collection", rs.getString("collection"));
        meta.put("section", rs.getString("section"));
        meta.put("summary", rs.getString("summary"));
        meta.put("chunk_key", rs.getString("chunk_key"));
        meta.put("version_id", rs.getObject("version_id"));
        meta.put("version", rs.getString("version"));
        meta.put("revision", rs.getInt("revision"));
        meta.put("content_hash", rs.getString("content_hash"));
        meta.put("status", "ACTIVE");
        String configId = rs.getString("embedding_config_id");
        if (StringUtils.hasText(configId)) {
            meta.put("embedding_config_id", configId);
        }
        String modelVersion = rs.getString("embedding_model_version");
        if (StringUtils.hasText(modelVersion)) {
            meta.put("embedding_model_version", modelVersion);
        }
        return new RetrievalResult.Hit(chunkId, content, score, meta);
    }

    private static EmbeddingClient unavailableClient() {
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
}
