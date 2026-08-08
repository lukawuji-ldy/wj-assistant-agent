package com.wuji.assistant.server.admin.auth;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.server.config.AdminJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Admin JWT 签发与解析。
 *
 * @author liudy
 */
@Service
public class AdminJwtService {

    private final AdminJwtProperties properties;
    private final SecretKey key;

    public AdminJwtService(AdminJwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 Admin 访问令牌。
     *
     * @param admin 管理员
     * @return JWT
     */
    public String issueToken(AdminAuthUser admin) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.getExpireHours(), ChronoUnit.HOURS);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(admin.adminId())
                .claim("adminId", admin.adminId())
                .claim("username", admin.username())
                .claim("role", admin.role())
                .claim("token_type", AdminAuthUser.TOKEN_TYPE_ADMIN)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * 解析为 AdminAuthUser；非 ADMIN token_type 则拒绝。
     *
     * @param token JWT
     * @return 管理员
     */
    public AdminAuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String tokenType = claims.get("token_type", String.class);
        if (!AdminAuthUser.TOKEN_TYPE_ADMIN.equals(tokenType)) {
            throw new IllegalArgumentException("not an admin token");
        }
        return AdminAuthUser.of(
                claims.get("adminId", String.class),
                claims.get("username", String.class),
                claims.get("role", String.class)
        );
    }
}
