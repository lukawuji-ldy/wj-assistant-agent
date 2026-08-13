package com.wuji.assistant.vta.server.security;

import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.vta.server.auth.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String token = header.substring(7).trim();
        try {
            AuthUser user = jwtService.parse(token);
            var auth = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().toUpperCase()))
            );
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        } catch (Exception ignore) {
            return chain.filter(exchange);
        }
    }
}

