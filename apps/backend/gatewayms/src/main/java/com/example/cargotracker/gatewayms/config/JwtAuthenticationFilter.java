package com.example.cargotracker.gatewayms.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/tracking",
            "/actuator"
    );

    /**
     * 公開接頭辞配下でも認証必須となる例外パス。
     *
     * <p>{@code /api/v1/tracking} は US18 で公開追跡照会のため認証不要だが、
     * trackingms 内部用 API（{@code POST /api/v1/tracking/_internal/issue-token}）は
     * 営業担当者の管理者 JWT が必須となる（ADR-0013）。</p>
     */
    private static final List<String> PRIVATE_PATH_OVERRIDES = List.of(
            "/api/v1/tracking/_internal"
    );

    private final SecretKey key;

    public JwtAuthenticationFilter(
            @Value("${app.jwt.secret:dev-secret-key-change-in-production-min-32-chars}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);

        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return chain.filter(exchange);
        } catch (RuntimeException e) {
            LOG.debug("JWT validation failed", e);
            return unauthorized(exchange);
        }
    }

    private boolean isPublicPath(String path) {
        if (PRIVATE_PATH_OVERRIDES.stream().anyMatch(path::startsWith)) {
            return false;
        }
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"message\":\"認証が必要です\"}".getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
