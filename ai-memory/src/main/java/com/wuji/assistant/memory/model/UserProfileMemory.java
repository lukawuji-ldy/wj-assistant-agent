package com.wuji.assistant.memory.model;

import java.time.Instant;

/**
 * 用户结构化长期记忆（画像 / 偏好）只读视图。
 *
 * @author liudy
 */
public class UserProfileMemory {

    private String memoryKey;
    private String memoryType;
    private String memoryValue;
    private float confidence;
    private float importance;
    private Instant lastUsedTime;
    private Instant updateTime;

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getMemoryValue() {
        return memoryValue;
    }

    public void setMemoryValue(String memoryValue) {
        this.memoryValue = memoryValue;
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
}
