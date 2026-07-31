package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Memory Extract 配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.memory")
public class WujiMemoryProperties {

    private Extract extract = new Extract();

    public Extract getExtract() {
        return extract;
    }

    public void setExtract(Extract extract) {
        this.extract = extract == null ? new Extract() : extract;
    }

    /**
     * L2 提取配置。
     *
     * @author liudy
     */
    public static class Extract {
        private boolean enabled = true;
        private boolean async = true;

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
    }
}
