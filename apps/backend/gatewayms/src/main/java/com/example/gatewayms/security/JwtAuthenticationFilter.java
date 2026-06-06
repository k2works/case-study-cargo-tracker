package com.example.gatewayms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * gatewayms の JWT 検証 GlobalFilter（IT9 A3.3 / US28 / ADR-0013 連携）。
 *
 * <p>{@code @Profile("heroku")} で本番のみ有効化。authms 発行の HS256 JWT を Authorization ヘッダから
 * 取り出して検証し、成功時に {@code X-Forwarded-User} / {@code X-Forwarded-Role} ヘッダを upstream
 * （各 ms）に付与する。失敗時は 401 Unauthorized を返す。</p>
 *
 * <p>公開 path（認証不要）:</p>
 * <ul>
 *   <li>{@code /api/auth/**}: ログイン / refresh</li>
 *   <li>{@code /api/v1/public/**}: 公開追跡照会（PublicTrackingTokenFilter で別途検証）</li>
 *   <li>{@code /api/v1/billing/webhooks/**}: Stripe webhook（HMAC 検証で代替認証）</li>
 *   <li>{@code /actuator/health}, {@code /actuator/info}: Heroku health check</li>
 * </ul>
 *
 * <p>JWT secret は authms と共有（Heroku Config Vars の {@code JWT_SECRET}）。
 * 各 ms 側で X-Forwarded-Role ヘッダを Spring Security の Authentication に変換する
 * PreAuthFilter は IT10 で実装予定。本 iteration では gatewayms 通過 = 認証済みとして扱う。</p>
 */
@Component
@Profile("heroku")
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/auth/",
            "/api/v1/public/",
            "/api/v1/billing/webhooks/",
            "/actuator/health",
            "/actuator/info"
    );

    private final SecretKey secretKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "jwt.secret は 32 バイト以上が必要です（現在: "
                            + (secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length)
                            + " バイト）");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("JWT 認証失敗: Authorization ヘッダ不在 path={}", path);
            return unauthorized(exchange);
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("JWT 検証失敗: path={}, reason={}", path, e.getMessage());
            return unauthorized(exchange);
        }
        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        if (username == null || role == null) {
            log.warn("JWT claim 不正: subject={}, role={}", username, role);
            return unauthorized(exchange);
        }
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r
                        .header("X-Forwarded-User", username)
                        .header("X-Forwarded-Role", role))
                .build();
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() {
        // ルーティング前に実行（NettyRoutingFilter は LOWEST_PRECEDENCE）
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private boolean isPublic(String path) {
        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
