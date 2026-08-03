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
    private int chunkSize = 500;
    private int chunkOverlap = 80;
    private int minChunkLengthToKeep = 50;
    private boolean chapterSplitEnabled = true;

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
}
