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
        private int maxMessageCount = 20;
        private int maxToken = 8000;
        private int compressMessageThreshold = 30;
        private int keepRecentMessages = 10;

        public int getMaxMessageCount() {
            return maxMessageCount;
        }

        public void setMaxMessageCount(int maxMessageCount) {
            this.maxMessageCount = maxMessageCount;
        }

        public int getMaxToken() {
            return maxToken;
        }

        public void setMaxToken(int maxToken) {
            this.maxToken = maxToken;
        }

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
