package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型路由配置（主备、重试）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.model")
public class WujiModelProperties {

    /** 主模型对应 llm_config.config_id */
    private String primaryConfigId = "llm_primary";

    /** 备用配置 id 列表（库中不存在或不可用则跳过） */
    private List<String> fallbackConfigIds = new ArrayList<>();

    /** 环境变量可覆盖库中 API Key（本地联调） */
    private String apiKeyOverride = "";

    private Retry retry = new Retry();

    /** 判定限流的 HTTP 状态码 */
    private List<Integer> rateLimitCodes = new ArrayList<>(List.of(429));

    public String getPrimaryConfigId() {
        return primaryConfigId;
    }

    public void setPrimaryConfigId(String primaryConfigId) {
        this.primaryConfigId = primaryConfigId;
    }

    public List<String> getFallbackConfigIds() {
        return fallbackConfigIds;
    }

    public void setFallbackConfigIds(List<String> fallbackConfigIds) {
        this.fallbackConfigIds = fallbackConfigIds != null ? fallbackConfigIds : new ArrayList<>();
    }

    public String getApiKeyOverride() {
        return apiKeyOverride;
    }

    public void setApiKeyOverride(String apiKeyOverride) {
        this.apiKeyOverride = apiKeyOverride;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public List<Integer> getRateLimitCodes() {
        return rateLimitCodes;
    }

    public void setRateLimitCodes(List<Integer> rateLimitCodes) {
        this.rateLimitCodes = rateLimitCodes != null ? rateLimitCodes : new ArrayList<>();
    }

    /**
     * 同配置重试参数。
     *
     * @author liudy
     */
    public static class Retry {

        private int maxAttempts = 3;

        private Duration backoff = Duration.ofSeconds(1);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration backoff) {
            this.backoff = backoff;
        }
    }
}
