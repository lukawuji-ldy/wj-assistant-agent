package com.wuji.assistant.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置。
 *
 * @author liudy
 */
@ConfigurationProperties(prefix = "wuji.security.jwt")
public class JwtProperties {

    /** HMAC 密钥，至少 32 字节 */
    private String secret = "change-me-wuji-assistant-jwt-secret-key-32b";

    private long expireHours = 72;

    private String issuer = "wuji-assistant";

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
