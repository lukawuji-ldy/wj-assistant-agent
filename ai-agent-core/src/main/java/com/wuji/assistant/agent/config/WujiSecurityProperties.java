package com.wuji.assistant.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全相关配置（API Key 加解密等）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.security")
public class WujiSecurityProperties {

    /**
     * AES 密钥材料，经 SHA-256 派生为 256-bit key；生产须用环境变量覆盖。
     */
    private String apiKeySecret = "change-me-wuji-api-key-secret-32bytes!!";

    public String getApiKeySecret() {
        return apiKeySecret;
    }

    public void setApiKeySecret(String apiKeySecret) {
        this.apiKeySecret = apiKeySecret;
    }
}
