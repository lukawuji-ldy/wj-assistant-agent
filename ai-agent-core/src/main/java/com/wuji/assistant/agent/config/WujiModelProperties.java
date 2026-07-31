package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型路由配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.model")
public class WujiModelProperties {

    /** 主模型对应 llm_config.config_id */
    private String primaryConfigId = "llm_primary";

    /** 环境变量可覆盖库中 API Key（本地联调） */
    private String apiKeyOverride = "";

    public String getPrimaryConfigId() {
        return primaryConfigId;
    }

    public void setPrimaryConfigId(String primaryConfigId) {
        this.primaryConfigId = primaryConfigId;
    }

    public String getApiKeyOverride() {
        return apiKeyOverride;
    }

    public void setApiKeyOverride(String apiKeyOverride) {
        this.apiKeyOverride = apiKeyOverride;
    }
}
