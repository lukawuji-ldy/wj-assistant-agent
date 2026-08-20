package com.wuji.assistant.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 向量后端配置（ai-rag 侧）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.rag")
public class RagVectorProperties {

    /** pgvector | elasticsearch */
    private String vectorBackend = "pgvector";

    private final Embedding embedding = new Embedding();
    private final Elasticsearch elasticsearch = new Elasticsearch();

    public String getVectorBackend() {
        return vectorBackend;
    }

    public void setVectorBackend(String vectorBackend) {
        this.vectorBackend = vectorBackend;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    public boolean isElasticsearchBackend() {
        return "elasticsearch".equalsIgnoreCase(vectorBackend);
    }

    /**
     * 批量 Embedding（入库 / 重建）限速与 429 退避；单 chunk 刷新不使用。
     */
    public static class Embedding {

        /** 两次成功请求最小间隔；0 关闭 */
        private long requestIntervalMs = 1000L;
        /** 单 chunk 遇 429 最大重试次数 */
        private int maxRetriesOn429 = 8;
        /** 首次退避毫秒；之后按 2^attempt 倍增 */
        private long retryBackoffMs = 2000L;
        /** 退避上限 */
        private long retryBackoffMaxMs = 60000L;

        public long getRequestIntervalMs() {
            return requestIntervalMs;
        }

        public void setRequestIntervalMs(long requestIntervalMs) {
            this.requestIntervalMs = requestIntervalMs;
        }

        public int getMaxRetriesOn429() {
            return maxRetriesOn429;
        }

        public void setMaxRetriesOn429(int maxRetriesOn429) {
            this.maxRetriesOn429 = maxRetriesOn429;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public long getRetryBackoffMaxMs() {
            return retryBackoffMaxMs;
        }

        public void setRetryBackoffMaxMs(long retryBackoffMaxMs) {
            this.retryBackoffMaxMs = retryBackoffMaxMs;
        }
    }

    /**
     * Elasticsearch 投影配置。
     */
    public static class Elasticsearch {

        private String uris = "http://127.0.0.1:9200";
        private String indexName = "wuji-kb-chunk";
        /** 索引 embedding 维度与当前模型不一致时自动删建索引（ES 仅为投影，需对 ACTIVE 版本重建向量） */
        private boolean recreateIndexOnDimensionMismatch = true;
        private final Hybrid hybrid = new Hybrid();
        private double minReliableScore = 0.35;

        public String getUris() {
            return uris;
        }

        public void setUris(String uris) {
            this.uris = uris;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public boolean isRecreateIndexOnDimensionMismatch() {
            return recreateIndexOnDimensionMismatch;
        }

        public void setRecreateIndexOnDimensionMismatch(boolean recreateIndexOnDimensionMismatch) {
            this.recreateIndexOnDimensionMismatch = recreateIndexOnDimensionMismatch;
        }

        public Hybrid getHybrid() {
            return hybrid;
        }

        public double getMinReliableScore() {
            return minReliableScore;
        }

        public void setMinReliableScore(double minReliableScore) {
            this.minReliableScore = minReliableScore;
        }
    }

    /**
     * Hybrid Search 参数。
     */
    public static class Hybrid {

        private boolean enabled = true;
        private int bm25Size = 20;
        private int knnSize = 20;
        private int rrfRankConstant = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBm25Size() {
            return bm25Size;
        }

        public void setBm25Size(int bm25Size) {
            this.bm25Size = bm25Size;
        }

        public int getKnnSize() {
            return knnSize;
        }

        public void setKnnSize(int knnSize) {
            this.knnSize = knnSize;
        }

        public int getRrfRankConstant() {
            return rrfRankConstant;
        }

        public void setRrfRankConstant(int rrfRankConstant) {
            this.rrfRankConstant = rrfRankConstant;
        }
    }
}
