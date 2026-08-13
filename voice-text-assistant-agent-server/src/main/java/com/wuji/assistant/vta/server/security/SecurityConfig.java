package com.wuji.assistant.vta.server.security;

import com.wuji.assistant.vta.server.config.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec;
import org.springframework.security.config.web.server.ServerHttpSecurity.HttpBasicSpec;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import org.springframework.http.HttpStatus;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({JwtProperties.class})
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            JwtAuthenticationFilter jwtFilter) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((exchange, denied) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return Mono.empty();
                        }))
                .authorizeExchange(ex -> ex
                        .pathMatchers("/api/health", "/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().authenticated())
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}

