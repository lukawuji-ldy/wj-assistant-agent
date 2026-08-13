package com.wuji.assistant.vta.server.auth;

import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.vta.server.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthUser(
                claims.get("userId", String.class),
                claims.get("username", String.class),
                claims.get("nickname", String.class),
                claims.get("tenantId", String.class),
                claims.get("role", String.class)
        );
    }

    public String issueToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.getExpireHours(), ChronoUnit.HOURS);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(user.userId())
                .claim("userId", user.userId())
                .claim("username", user.username())
                .claim("nickname", user.nickname())
                .claim("tenantId", user.tenantId())
                .claim("role", user.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }
}

