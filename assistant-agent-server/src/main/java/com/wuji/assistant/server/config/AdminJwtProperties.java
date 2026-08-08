package com.wuji.assistant.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin JWT 配置（与聊天 User JWT 双轨隔离）。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.admin.security.jwt")
public class AdminJwtProperties {

    /** HMAC 密钥，至少 32 字节；须与 User JWT secret 不同 */
    private String secret = "change-me-wuji-admin-jwt-secret-key-32bytes";

    private long expireHours = 24;

    private String issuer = "wuji-assistant-admin";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireHours() {
        return expireHours;
    }

    public void setExpireHours(long expireHours) {
        this.expireHours = expireHours;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
