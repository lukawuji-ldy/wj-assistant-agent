package com.wuji.assistant.server.security;

import com.wuji.assistant.common.auth.AdminAuthUser;
import com.wuji.assistant.common.auth.AuthUser;
import com.wuji.assistant.server.admin.auth.AdminJwtService;
import com.wuji.assistant.server.auth.JwtService;
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

/**
 * 按路径分流解析 User JWT / Admin JWT，写入 Reactive SecurityContext。
 * <p>
 * {@code /api/admin/**} 仅接受 Admin JWT；其余业务 API 仅接受 User JWT。串用视为未认证。
 *
 * @author liudy
 */
@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtService jwtService;
    private final AdminJwtService adminJwtService;

    public JwtAuthenticationFilter(JwtService jwtService, AdminJwtService adminJwtService) {
        this.jwtService = jwtService;
        this.adminJwtService = adminJwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String token = header.substring(7).trim();
        boolean adminPath = path.startsWith("/api/admin");
        try {
            if (adminPath) {
                AdminAuthUser admin = adminJwtService.parse(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        admin, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN_" + admin.role().toUpperCase())));
                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
            }
            AuthUser user = jwtService.parse(token);
            var auth = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().toUpperCase())));
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        } catch (Exception ex) {
            return chain.filter(exchange);
        }
    }
}
