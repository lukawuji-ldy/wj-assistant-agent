package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Agent / 提示词 / 流式续传配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.agent")
public class WujiAgentProperties {

    private String systemPromptCode = "agent.default.system";
    private String userPromptCode = "agent.default.user";
    private int memoryWindowSize = 20;
    private Stream stream = new Stream();

    public String getSystemPromptCode() {
        return systemPromptCode;
    }

    public void setSystemPromptCode(String systemPromptCode) {
        this.systemPromptCode = systemPromptCode;
    }

    public String getUserPromptCode() {
        return userPromptCode;
    }

    public void setUserPromptCode(String userPromptCode) {
        this.userPromptCode = userPromptCode;
    }

    public int getMemoryWindowSize() {
        return memoryWindowSize;
    }

    public void setMemoryWindowSize(int memoryWindowSize) {
        this.memoryWindowSize = memoryWindowSize;
    }

    public Stream getStream() {
        return stream;
    }

    public void setStream(Stream stream) {
        this.stream = stream;
    }

    /**
     * SSE 心跳与续传缓冲配置。
     *
     * @author liudy
     */
    public static class Stream {

        /** 心跳间隔 */
        private Duration heartbeatInterval = Duration.ofSeconds(15);

        /** 续传缓冲最大事件数 */
        private int resumeBufferEvents = 200;

        /** 流结束后缓冲保留时长 */
        private Duration resumeTtl = Duration.ofMinutes(10);

        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public int getResumeBufferEvents() {
            return resumeBufferEvents;
        }

        public void setResumeBufferEvents(int resumeBufferEvents) {
            this.resumeBufferEvents = resumeBufferEvents;
        }

        public Duration getResumeTtl() {
            return resumeTtl;
        }

        public void setResumeTtl(Duration resumeTtl) {
            this.resumeTtl = resumeTtl;
        }
    }
}
