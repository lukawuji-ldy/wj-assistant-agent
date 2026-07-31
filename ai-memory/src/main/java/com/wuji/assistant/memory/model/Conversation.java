package com.wuji.assistant.memory.model;

import java.time.OffsetDateTime;

/**
 * 会话实体。
 *
 * @author liudy
 */
public class Conversation {

    private Long id;
    private String conversationId;
    private String userId;
    private String title;
    private String summary;
    private OffsetDateTime summaryUntilTime;
    private String summaryUntilMessageId;
    private OffsetDateTime summaryCompressedAt;
    private int messageCount;
    private OffsetDateTime lastActiveTime;
    private OffsetDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public OffsetDateTime getSummaryUntilTime() {
        return summaryUntilTime;
    }

    public void setSummaryUntilTime(OffsetDateTime summaryUntilTime) {
        this.summaryUntilTime = summaryUntilTime;
    }

    public String getSummaryUntilMessageId() {
        return summaryUntilMessageId;
    }

    public void setSummaryUntilMessageId(String summaryUntilMessageId) {
        this.summaryUntilMessageId = summaryUntilMessageId;
    }

    public OffsetDateTime getSummaryCompressedAt() {
        return summaryCompressedAt;
    }

    public void setSummaryCompressedAt(OffsetDateTime summaryCompressedAt) {
        this.summaryCompressedAt = summaryCompressedAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public OffsetDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(OffsetDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }
}
