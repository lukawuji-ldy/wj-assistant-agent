package com.wuji.assistant.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Memory 配置（短记忆 / 摘要 / Extract / L3）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.memory")
public class WujiMemoryProperties {

    private ShortTerm shortTerm = new ShortTerm();
    private Router router = new Router();
    private Retrieve retrieve = new Retrieve();
    private Extract extract = new Extract();
    private Lifecycle lifecycle = new Lifecycle();

    public ShortTerm getShortTerm() {
        return shortTerm;
    }

    public void setShortTerm(ShortTerm shortTerm) {
        this.shortTerm = shortTerm == null ? new ShortTerm() : shortTerm;
    }

    /** YAML 键 {@code short} */
    public ShortTerm getShort() {
        return shortTerm;
    }

    public void setShort(ShortTerm shortTerm) {
        setShortTerm(shortTerm);
    }

    public Router getRouter() {
        return router;
    }

    public void setRouter(Router router) {
        this.router = router == null ? new Router() : router;
    }

    public Retrieve getRetrieve() {
        return retrieve;
    }

    public void setRetrieve(Retrieve retrieve) {
        this.retrieve = retrieve == null ? new Retrieve() : retrieve;
    }

    public Extract getExtract() {
        return extract;
    }

    public void setExtract(Extract extract) {
        this.extract = extract == null ? new Extract() : extract;
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Lifecycle lifecycle) {
        this.lifecycle = lifecycle == null ? new Lifecycle() : lifecycle;
    }

    /**
     * 短记忆窗口。
     */
    public static class ShortTerm {
        /** 未摘要消息条数达到该阈值触发会话摘要压缩（按 watermark 后的增量计数） */
        private int compressMessageThreshold = 30;
        /** 摘要压缩后保留的最近消息条数，作为窗口余量避免出现未摘要盲区 */
        private int keepRecentMessages = 10;
        /** 未摘要消息估算 Token 数达到该阈值触发会话摘要压缩 */
        private int compressTokenThreshold = 12000;
        /** 是否启用 LLM 摘要；关闭时降级为规则摘要 */
        private boolean summaryLlmEnabled = true;
        /** 异步 LLM 摘要调用超时时间，超时降级规则摘要 */
        private Duration summaryTimeout = Duration.ofSeconds(20);
        /** 是否在本轮同步执行 LLM 摘要；false 时异步丢入 llmCallScheduler */
        private boolean summarySyncEnabled = false;
        /** 同步摘要超时时间（summarySyncEnabled=true 时生效） */
        private Duration summarySyncTimeout = Duration.ofSeconds(5);
        /** 摘要系统提示词编码（对应 prompt_template 主表 key） */
        private String summarySystemPromptCode = "memory.summary.system";
        /** 摘要用户提示词编码（对应 prompt_template 主表 key） */
        private String summaryUserPromptCode = "memory.summary.user";

        public int getCompressMessageThreshold() {
            return compressMessageThreshold;
        }

        public void setCompressMessageThreshold(int compressMessageThreshold) {
            this.compressMessageThreshold = compressMessageThreshold;
        }

        public int getKeepRecentMessages() {
            return keepRecentMessages;
        }

        public void setKeepRecentMessages(int keepRecentMessages) {
            this.keepRecentMessages = keepRecentMessages;
        }

        /**
         * 短期窗口条数上限（派生值）：未摘要条数恒不超过 {@link #compressMessageThreshold}，
         * 加 {@link #keepRecentMessages} 余量即可完整覆盖，避免出现未摘要盲区。
         *
         * @return 窗口上限，至少 1
         */
        public int windowSize() {
            long size = (long) compressMessageThreshold + keepRecentMessages;
            return (int) Math.max(1, Math.min(size, Integer.MAX_VALUE));
        }

        public int getCompressTokenThreshold() {
            return compressTokenThreshold;
        }

        public void setCompressTokenThreshold(int compressTokenThreshold) {
            this.compressTokenThreshold = Math.max(0, compressTokenThreshold);
        }

        public boolean isSummaryLlmEnabled() {
            return summaryLlmEnabled;
        }

        public void setSummaryLlmEnabled(boolean summaryLlmEnabled) {
            this.summaryLlmEnabled = summaryLlmEnabled;
        }

        public Duration getSummaryTimeout() {
            return summaryTimeout;
        }

        public void setSummaryTimeout(Duration summaryTimeout) {
            this.summaryTimeout = summaryTimeout == null || summaryTimeout.isNegative()
                    ? Duration.ofSeconds(20) : summaryTimeout;
        }

        public boolean isSummarySyncEnabled() {
            return summarySyncEnabled;
        }

        public void setSummarySyncEnabled(boolean summarySyncEnabled) {
            this.summarySyncEnabled = summarySyncEnabled;
        }

        public Duration getSummarySyncTimeout() {
            return summarySyncTimeout;
        }

        public void setSummarySyncTimeout(Duration summarySyncTimeout) {
            this.summarySyncTimeout = summarySyncTimeout == null || summarySyncTimeout.isNegative()
                    || summarySyncTimeout.isZero()
                    ? Duration.ofSeconds(5) : summarySyncTimeout;
        }

        public String getSummarySystemPromptCode() {
            return summarySystemPromptCode;
        }

        public void setSummarySystemPromptCode(String summarySystemPromptCode) {
            this.summarySystemPromptCode = summarySystemPromptCode == null || summarySystemPromptCode.isBlank()
                    ? "memory.summary.system" : summarySystemPromptCode.trim();
        }

        public String getSummaryUserPromptCode() {
            return summaryUserPromptCode;
        }

        public void setSummaryUserPromptCode(String summaryUserPromptCode) {
            this.summaryUserPromptCode = summaryUserPromptCode == null || summaryUserPromptCode.isBlank()
                    ? "memory.summary.user" : summaryUserPromptCode.trim();
        }
    }

    /**
     * 长期记忆 Router：rule | hybrid（hybrid 失败降级 rule）。
     */
    public static class Router {
        /** rule | hybrid */
        private String mode = "rule";
        private Duration timeout = Duration.ofSeconds(2);
        private String systemPromptCode = "memory.retrieve.router.system";
        private String userPromptCode = "memory.retrieve.router.user";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode == null || mode.isBlank() ? "rule" : mode.trim().toLowerCase();
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
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
    }

    /**
     * 长期记忆召回截断与评分权重。
     */
    public static class Retrieve {
        private int topK = 8;
        private double weightSimilarity = 0.5;
        private double weightConfidence = 0.2;
        private double weightFreshness = 0.2;
        private double weightImportance = 0.2;
        private boolean semanticEnabled = true;
        private int semanticTopK = 4;
        private double semanticMinScore = 0.55;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK <= 0 ? 8 : topK;
        }

        public double getWeightSimilarity() {
            return weightSimilarity;
        }

        public void setWeightSimilarity(double weightSimilarity) {
            this.weightSimilarity = weightSimilarity;
        }

        public double getWeightConfidence() {
            return weightConfidence;
        }

        public void setWeightConfidence(double weightConfidence) {
            this.weightConfidence = weightConfidence;
        }

        public double getWeightFreshness() {
            return weightFreshness;
        }

        public void setWeightFreshness(double weightFreshness) {
            this.weightFreshness = weightFreshness;
        }

        public double getWeightImportance() {
            return weightImportance;
        }

        public void setWeightImportance(double weightImportance) {
            this.weightImportance = weightImportance;
        }

        public boolean isSemanticEnabled() {
            return semanticEnabled;
        }

        public void setSemanticEnabled(boolean semanticEnabled) {
            this.semanticEnabled = semanticEnabled;
        }

        public int getSemanticTopK() {
            return semanticTopK;
        }

        public void setSemanticTopK(int semanticTopK) {
            this.semanticTopK = semanticTopK <= 0 ? 4 : semanticTopK;
        }

        public double getSemanticMinScore() {
            return semanticMinScore;
        }

        public void setSemanticMinScore(double semanticMinScore) {
            this.semanticMinScore = semanticMinScore;
        }
    }

    /**
     * L2 提取配置。
     */
    public static class Extract {
        private boolean enabled = true;
        private boolean async = true;
        private boolean explicitDetectEnabled = true;
        /** rule | llm | hybrid */
        private String mode = "hybrid";
        private String systemPromptCode = "memory.extract.system";
        private String userPromptCode = "memory.extract.user";
        private Duration timeout = Duration.ofSeconds(20);
        private double minConfidence = 0.55;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAsync() {
            return async;
        }

        public void setAsync(boolean async) {
            this.async = async;
        }

        public boolean isExplicitDetectEnabled() {
            return explicitDetectEnabled;
        }

        public void setExplicitDetectEnabled(boolean explicitDetectEnabled) {
            this.explicitDetectEnabled = explicitDetectEnabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode == null || mode.isBlank() ? "hybrid" : mode.trim().toLowerCase();
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

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        }

        public double getMinConfidence() {
            return minConfidence;
        }

        public void setMinConfidence(double minConfidence) {
            this.minConfidence = minConfidence;
        }
    }

    /**
     * L3 生命周期。
     */
    public static class Lifecycle {
        private String consolidateCron = "0 0 3 * * ?";
        private boolean consolidateEnabled = true;

        public String getConsolidateCron() {
            return consolidateCron;
        }

        public void setConsolidateCron(String consolidateCron) {
            this.consolidateCron = consolidateCron;
        }

        public boolean isConsolidateEnabled() {
            return consolidateEnabled;
        }

        public void setConsolidateEnabled(boolean consolidateEnabled) {
            this.consolidateEnabled = consolidateEnabled;
        }
    }
}
