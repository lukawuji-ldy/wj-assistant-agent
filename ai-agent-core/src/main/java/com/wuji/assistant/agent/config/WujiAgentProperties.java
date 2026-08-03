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

    /** Agent 业务名（ReactAgent.name 前缀） */
    private String id = "default";

    private String systemPromptCode = "agent.default.system";
    private String userPromptCode = "agent.default.user";
    private int memoryWindowSize = 20;

    /** 工具调用最大轮次，禁止无限循环 */
    private int maxToolRounds = 8;

    /** 单次用户请求内模型调用上限（含工具循环） */
    private int maxModelCalls = 16;

    private Checkpoint checkpoint = new Checkpoint();

    private Stream stream = new Stream();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public int getMaxModelCalls() {
        return maxModelCalls;
    }

    public void setMaxModelCalls(int maxModelCalls) {
        this.maxModelCalls = maxModelCalls;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint == null ? new Checkpoint() : checkpoint;
    }

    public Stream getStream() {
        return stream;
    }

    public void setStream(Stream stream) {
        this.stream = stream;
    }

    /**
     * Checkpoint Saver 配置。
     *
     * @author liudy
     */
    public static class Checkpoint {

        /** postgres | memory */
        private String type = "postgres";

        /** 默认 false：表由 schema/Flyway 管理；SAA 1.1.2.2 建索引非幂等 */
        private boolean createTables = false;

        private String host = "127.0.0.1";

        private int port = 5432;

        private String database = "vector_test";

        private String username = "postgres";

        private String password = "";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isCreateTables() {
            return createTables;
        }

        public void setCreateTables(boolean createTables) {
            this.createTables = createTables;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
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
