package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 专用 Scheduler 配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.scheduler")
public class WujiSchedulerProperties {

    private Pool db = new Pool(20, 1000);
    private Pool chatTail = new Pool(16, 5000);
    private Pool kbIngest = new Pool(4, 100);
    private Pool llmCall = new Pool(32, 500);
    private Pool kbTest = new Pool(4, 50);

    public Pool getDb() {
        return db;
    }

    public void setDb(Pool db) {
        this.db = db == null ? new Pool() : db;
    }

    public Pool getChatTail() {
        return chatTail;
    }

    public void setChatTail(Pool chatTail) {
        this.chatTail = chatTail == null ? new Pool() : chatTail;
    }

    public Pool getKbIngest() {
        return kbIngest;
    }

    public void setKbIngest(Pool kbIngest) {
        this.kbIngest = kbIngest == null ? new Pool() : kbIngest;
    }

    public Pool getLlmCall() {
        return llmCall;
    }

    public void setLlmCall(Pool llmCall) {
        this.llmCall = llmCall == null ? new Pool() : llmCall;
    }

    public Pool getKbTest() {
        return kbTest;
    }

    public void setKbTest(Pool kbTest) {
        this.kbTest = kbTest == null ? new Pool() : kbTest;
    }

    public static class Pool {
        private int threadCap = 10;
        private int queuedTaskCap = 100;

        public Pool() {
        }

        public Pool(int threadCap, int queuedTaskCap) {
            this.threadCap = threadCap;
            this.queuedTaskCap = queuedTaskCap;
        }

        public int getThreadCap() {
            return threadCap;
        }

        public void setThreadCap(int threadCap) {
            this.threadCap = Math.max(1, threadCap);
        }

        public int getQueuedTaskCap() {
            return queuedTaskCap;
        }

        public void setQueuedTaskCap(int queuedTaskCap) {
            this.queuedTaskCap = Math.max(1, queuedTaskCap);
        }
    }
}
