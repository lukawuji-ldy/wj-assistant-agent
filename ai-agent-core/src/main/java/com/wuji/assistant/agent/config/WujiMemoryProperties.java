package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Memory 配置（短记忆 / 摘要 / Extract / L3）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.memory")
public class WujiMemoryProperties {

    private ShortTerm shortTerm = new ShortTerm();
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
     * L2 提取配置。
     */
    public static class Extract {
        private boolean enabled = true;
        private boolean async = true;
        private boolean explicitDetectEnabled = true;

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
