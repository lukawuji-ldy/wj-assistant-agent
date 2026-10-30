package com.wuji.assistant.vta.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wuji.vta.scheduler")
public class VtaSchedulerProperties {

    private Pool db = new Pool(10, 500);
    private Pool llmCall = new Pool(16, 200);

    public Pool getDb() {
        return db;
    }

    public void setDb(Pool db) {
        this.db = db == null ? new Pool() : db;
    }

    public Pool getLlmCall() {
        return llmCall;
    }

    public void setLlmCall(Pool llmCall) {
        this.llmCall = llmCall == null ? new Pool() : llmCall;
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
