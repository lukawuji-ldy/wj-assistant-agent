package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.rag")
public class WujiRagProperties {

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
    private final Splitter splitter = new Splitter();
    private final Preprocess preprocess = new Preprocess();

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

    public Splitter getSplitter() {
        return splitter;
    }

    public Preprocess getPreprocess() {
        return preprocess;
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
