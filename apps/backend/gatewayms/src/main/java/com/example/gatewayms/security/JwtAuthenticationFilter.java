package com.example.gatewayms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * JWT の署名検証を行う唯一の場所（ADR-004）。
 *
 * <p>検証に成功したら、利用者 ID とロールをヘッダに詰めて下流へ渡す。下流のサービスは
 * 署名を見ずこのヘッダを信頼するため、ここを通らない経路が生まれないことが前提になる。
 */
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * ヘルスチェックの経路。認証を要求しない。
     *
     * <p>Kubernetes の probe は認証情報を持てないため、横断的な防御を一律に適用すると
     * 正常に動いているサービスが 401 で「異常」と判定され、再起動ループに入る。
     * 一方で {@code /actuator} 全体を開けると設定値や環境変数が外から読めるため、
     * health の配下だけを明示的に許す。
     */
    private static final List<String> HEALTH_PATHS =
            List.of("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness");

    private final PublicPathMatcher publicPathMatcher;
    private final SecretKey key;

    public JwtAuthenticationFilter(PublicPathMatcher publicPathMatcher, String secret) {
        this.publicPathMatcher = publicPathMatcher;
        this.key = JwtKeys.hmacKeyOf(secret);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getURI().getPath();

        if (isHealthProbe(path) || publicPathMatcher.isPublic(method, path)) {
            // 公開経路でもクレームヘッダは剥がす。残すと認証なしで管理者を名乗れる
            return chain.filter(withoutClientClaims(exchange));
        }

        String token = bearerTokenOf(request);
        if (token == null) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return chain.filter(withVerifiedClaims(exchange, claims));
        } catch (JwtException | IllegalArgumentException e) {
            // 失敗の理由（期限切れ・署名不一致）は応答で区別しない
            return unauthorized(exchange);
        }
    }

    private boolean isHealthProbe(String path) {
        return HEALTH_PATHS.contains(path);
    }

    private String bearerTokenOf(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private ServerWebExchange withoutClientClaims(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(builder -> builder
                        .headers(headers -> {
                            headers.remove(AuthenticatedUserHeaders.USER_ID);
                            headers.remove(AuthenticatedUserHeaders.ROLES);
                        }))
                .build();
    }

    private ServerWebExchange withVerifiedClaims(ServerWebExchange exchange, Claims claims) {
        String roles = String.join(",", claims.get("roles", List.class));
        return exchange.mutate()
                .request(builder -> builder
                        .headers(headers -> {
                            // set は既存値を置き換える。利用者が名乗った値を残さない
                            headers.set(AuthenticatedUserHeaders.USER_ID, claims.getSubject());
                            headers.set(AuthenticatedUserHeaders.ROLES, roles);
                        }))
                .build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
