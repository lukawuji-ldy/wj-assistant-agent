package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.rag")
public class WujiRagProperties {

    /** pgvector | elasticsearch */
    private String vectorBackend = "pgvector";

    private boolean asTool = true;
    private int topK = 5;
    private double similarityThreshold = 0.72;
    private double minReliableScore = 0.72;
    private String defaultCollection = "kb_default";
    private String answerMode = "grounded";
    private boolean embeddingEnabled = true;
    private String embeddingConfigId = "llm_embedding";
    private String toolDescriptionPromptCode = "rag.knowledge_retrieval.system";
    private String answerPromptCode = "rag.answer.system";
    /** grounded 下先检索再拼 System，避免模型跳过 knowledge_retrieval */
    private boolean prefetchEnabled = true;
    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private int minChunkLengthToKeep = 50;
    private boolean chapterSplitEnabled = true;
    private final Embedding embedding = new Embedding();
    private final Splitter splitter = new Splitter();
    private final Preprocess preprocess = new Preprocess();
    private final Elasticsearch elasticsearch = new Elasticsearch();

    public String getVectorBackend() {
        return vectorBackend;
    }

    public void setVectorBackend(String vectorBackend) {
        this.vectorBackend = vectorBackend;
    }

    public boolean isElasticsearchBackend() {
        return "elasticsearch".equalsIgnoreCase(vectorBackend);
    }

    /**
     * 检索可靠分阈值：ES 模式用 elasticsearch.min-reliable-score，否则 min-reliable-score。
     */
    public double getEffectiveMinReliableScore() {
        if (isElasticsearchBackend()) {
            return elasticsearch.getMinReliableScore();
        }
        return minReliableScore;
    }

    public boolean isAsTool() {
        return asTool;
    }

    public void setAsTool(boolean asTool) {
        this.asTool = asTool;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public double getMinReliableScore() {
        return minReliableScore;
    }

    public void setMinReliableScore(double minReliableScore) {
        this.minReliableScore = minReliableScore;
    }

    public String getDefaultCollection() {
        return defaultCollection;
    }

    public void setDefaultCollection(String defaultCollection) {
        this.defaultCollection = defaultCollection;
    }

    public String getAnswerMode() {
        return answerMode;
    }

    public void setAnswerMode(String answerMode) {
        this.answerMode = answerMode;
    }

    public boolean isEmbeddingEnabled() {
        return embeddingEnabled;
    }

    public void setEmbeddingEnabled(boolean embeddingEnabled) {
        this.embeddingEnabled = embeddingEnabled;
    }

    public String getEmbeddingConfigId() {
        return embeddingConfigId;
    }

    public void setEmbeddingConfigId(String embeddingConfigId) {
        this.embeddingConfigId = embeddingConfigId;
    }

    public String getToolDescriptionPromptCode() {
        return toolDescriptionPromptCode;
    }

    public void setToolDescriptionPromptCode(String toolDescriptionPromptCode) {
        this.toolDescriptionPromptCode = toolDescriptionPromptCode;
    }

    public String getAnswerPromptCode() {
        return answerPromptCode;
    }

    public void setAnswerPromptCode(String answerPromptCode) {
        this.answerPromptCode = answerPromptCode;
    }

    public boolean isPrefetchEnabled() {
        return prefetchEnabled;
    }

    public void setPrefetchEnabled(boolean prefetchEnabled) {
        this.prefetchEnabled = prefetchEnabled;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getMinChunkLengthToKeep() {
        return minChunkLengthToKeep;
    }

    public void setMinChunkLengthToKeep(int minChunkLengthToKeep) {
        this.minChunkLengthToKeep = minChunkLengthToKeep;
    }

    public boolean isChapterSplitEnabled() {
        return chapterSplitEnabled;
    }

    public void setChapterSplitEnabled(boolean chapterSplitEnabled) {
        this.chapterSplitEnabled = chapterSplitEnabled;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public Splitter getSplitter() {
        return splitter;
    }

    public Preprocess getPreprocess() {
        return preprocess;
    }

    public Elasticsearch getElasticsearch() {
        return elasticsearch;
    }

    /**
     * 批量 Embedding（入库 / 重建）限速与 429 退避；单 chunk 刷新不使用。
     * 实际执行侧见 {@code ai-rag} {@code RagVectorProperties.Embedding} / {@code EmbeddingBulkThrottle}。
     */
    public static class Embedding {

        private long requestIntervalMs = 1000L;
        private int maxRetriesOn429 = 8;
        private long retryBackoffMs = 2000L;
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

    /**
     * 切分器扩展配置。
     */
    public static class Splitter {
        private String chapterPattern =
                "(?m)^([一二三四五六七八九十百千]+、|第[一二三四五六七八九十百千\\d]+[章节篇]|\\d+(\\.\\d+)*\\s+)";
        private String sectionTitleMode = "FULL_LINE";
        private java.util.List<String> separators =
                java.util.List.of("\n\n", "\n", "。", "！", "？", "；", "，");
        private String keepSeparator = "APPEND";

        public String getChapterPattern() {
            return chapterPattern;
        }

        public void setChapterPattern(String chapterPattern) {
            this.chapterPattern = chapterPattern;
        }

        public String getSectionTitleMode() {
            return sectionTitleMode;
        }

        public void setSectionTitleMode(String sectionTitleMode) {
            this.sectionTitleMode = sectionTitleMode;
        }

        public java.util.List<String> getSeparators() {
            return separators;
        }

        public void setSeparators(java.util.List<String> separators) {
            this.separators = separators;
        }

        public String getKeepSeparator() {
            return keepSeparator;
        }

        public void setKeepSeparator(String keepSeparator) {
            this.keepSeparator = keepSeparator;
        }
    }

    /**
     * 预处理扩展配置。
     */
    public static class Preprocess {
        private boolean normalizeNewlines = true;
        private boolean stripPageNumbers = true;
        private boolean mergeCjkHardWrap = true;
        private boolean collapseBlankLines = true;
        private boolean trimOutsideChapters = false;
        private java.util.List<String> trailingNoiseMarkers = java.util.List.of("复制上面这段");

        public boolean isNormalizeNewlines() {
            return normalizeNewlines;
        }

        public void setNormalizeNewlines(boolean normalizeNewlines) {
            this.normalizeNewlines = normalizeNewlines;
        }

        public boolean isStripPageNumbers() {
            return stripPageNumbers;
        }

        public void setStripPageNumbers(boolean stripPageNumbers) {
            this.stripPageNumbers = stripPageNumbers;
        }

        public boolean isMergeCjkHardWrap() {
            return mergeCjkHardWrap;
        }

        public void setMergeCjkHardWrap(boolean mergeCjkHardWrap) {
            this.mergeCjkHardWrap = mergeCjkHardWrap;
        }

        public boolean isCollapseBlankLines() {
            return collapseBlankLines;
        }

        public void setCollapseBlankLines(boolean collapseBlankLines) {
            this.collapseBlankLines = collapseBlankLines;
        }

        public boolean isTrimOutsideChapters() {
            return trimOutsideChapters;
        }

        public void setTrimOutsideChapters(boolean trimOutsideChapters) {
            this.trimOutsideChapters = trimOutsideChapters;
        }

        public java.util.List<String> getTrailingNoiseMarkers() {
            return trailingNoiseMarkers;
        }

        public void setTrailingNoiseMarkers(java.util.List<String> trailingNoiseMarkers) {
            this.trailingNoiseMarkers = trailingNoiseMarkers;
        }
    }
}
