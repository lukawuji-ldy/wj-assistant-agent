package com.wuji.assistant.memory.model;

import java.time.Instant;

/**
 * 用户语义长期记忆检索命中。
 *
 * @author liudy
 */
public class UserSemanticHit {

    private String id;
    private String content;
    private float confidence;
    private float importance;
    private Instant lastUsedTime;
    private Instant updateTime;
    /** 余弦相似度 (1 - distance)，约 0～1 */
    private double score;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public float getImportance() {
        return importance;
    }

    public void setImportance(float importance) {
        this.importance = importance;
    }

    public Instant getLastUsedTime() {
        return lastUsedTime;
    }

    public void setLastUsedTime(Instant lastUsedTime) {
        this.lastUsedTime = lastUsedTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
