package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Agent / 鎻愮ず璇?/ 娴佸紡缁紶閰嶇疆銆?
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.agent")
public class WujiAgentProperties {

    /** Agent 涓氬姟鍚嶏紙ReactAgent.name 鍓嶇紑锛?*/
    private String id = "default";

    private String systemPromptCode = "agent.default.system";
    private String userPromptCode = "agent.default.user";

    /** 宸ュ叿璋冪敤鏈€澶ц疆娆★紝绂佹鏃犻檺寰幆 */
    private int maxToolRounds = 8;

    /** 鍗曟鐢ㄦ埛璇锋眰鍐呮ā鍨嬭皟鐢ㄤ笂闄愶紙鍚伐鍏峰惊鐜級 */
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
     * Checkpoint Saver 閰嶇疆銆?
     *
     * @author liudy
     */
    public static class Checkpoint {

        /** postgres | memory */
        private String type = "postgres";

        /** 榛樿 false锛氳〃鐢?schema/Flyway 绠＄悊锛汼AA 1.1.2.2 寤虹储寮曢潪骞傜瓑 */
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
     * SSE 蹇冭烦涓庣画浼犵紦鍐查厤缃€?
     *
     * @author liudy
     */
    public static class Stream {

        /** 蹇冭烦闂撮殧 */
        private Duration heartbeatInterval = Duration.ofSeconds(15);

        /** 缁紶缂撳啿鏈€澶т簨浠舵暟 */
        private int resumeBufferEvents = 200;

        /** 娴佺粨鏉熷悗缂撳啿淇濈暀鏃堕暱 */
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
