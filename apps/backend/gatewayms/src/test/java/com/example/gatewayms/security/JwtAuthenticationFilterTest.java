package com.example.gatewayms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.auth.AuthenticatedUser;
import io.jsonwebtoken.Jwts;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@DisplayName("Gateway の JWT 検証フィルタ")
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm";

    private final SecretKey key = JwtKeys.hmacKeyOf(SECRET);
    private final PublicPathMatcher matcher = new PublicPathMatcher(List.of(
            new PublicPath(HttpMethod.GET, "/api/v1/public/tracking/*"),
            new PublicPath(HttpMethod.POST, "/api/v1/auth/login")));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(matcher, SECRET);

    private ServerWebExchange forwarded;

    /** フィルタチェーンの下流。ここに到達したかどうかで「通したか」を判定する。 */
    private Mono<Void> chain(ServerWebExchange exchange) {
        forwarded = exchange;
        return Mono.empty();
    }

    private String tokenFor(String userId, List<String> roles) {
        return Jwts.builder().subject(userId).claim("roles", roles).signWith(key).compact();
    }

    private MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    @Nested
    @DisplayName("公開経路")
    class Public {

        @Test
        @DisplayName("認証なしでも通す")
        void passesWithoutToken() {
            MockServerWebExchange exchange =
                    exchange(MockServerHttpRequest.get("/api/v1/public/tracking/TRK-1").build());

            filter.filter(exchange, JwtAuthenticationFilterTest.this::chain).block();

            assertThat(forwarded).isNotNull();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    @Nested
    @DisplayName("ヘルスチェック")
    class HealthProbe {

        /**
         * 横断的な防御をヘルスチェックにも一律で適用すると、認証を通せない Kubernetes の
         * probe が 401 を受け取り、正常に動いているサービスが再起動ループに入る。
         * IT1 の kind 統合で実際にこの形の停止が起きた。
         */
        @Test
        @DisplayName("認証を要求せずに通す（probe は認証情報を持てない）")
        void passesWithoutAuthentication() {
            for (String path : new String[] {
                "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"
            }) {
                forwarded = null;
                MockServerWebExchange exchange = exchange(MockServerHttpRequest.get(path).build());

                filter.filter(exchange, JwtAuthenticationFilterTest.this::chain).block();

                assertThat(exchange.getResponse().getStatusCode())
                        .as("%s が 401 を返すと probe が失敗し、再起動ループになる", path)
                        .isNull();
                assertThat(forwarded).as("%s が下流へ通っていない", path).isNotNull();
            }
        }

        @Test
        @DisplayName("運用情報を晒す actuator の他の経路までは開けない")
        void doesNotOpenOtherActuatorEndpoints() {
            MockServerWebExchange exchange =
                    exchange(MockServerHttpRequest.get("/actuator/env").build());

            filter.filter(exchange, JwtAuthenticationFilterTest.this::chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("保護経路")
    class Protected {

        @Test
        @DisplayName("トークンが無ければ 401 で止める")
        void rejectsMissingToken() {
            MockServerWebExchange exchange =
                    exchange(MockServerHttpRequest.get("/api/v1/bookings").build());

            filter.filter(exchange, JwtAuthenticationFilterTest.this::chain).block();

            assertThat(forwarded).as("認証なしで下流へ転送された").isNull();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("署名が異なるトークンは 401 で止める")
        void rejectsTokenSignedWithAnotherKey() {
            String forged = Jwts.builder()
                    .subject("attacker")
                    .claim("roles", List.of("ROLE_ADMIN"))
                    .signWith(JwtKeys.hmacKeyOf("another-secret-key-that-is-also-long-enough-for-hs256"))
                    .compact();
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/bookings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                    .build());

            filter.filter(exchange, JwtAuthenticationFilterTest.this::chain).block();

            assertThat(forwarded).as("偽造トークンが下流へ転送された").isNull();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("正しいトークンなら検証済みのクレームを付けて転送する")
        void forwardsVerifiedClaims() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/bookings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("sales01", List.of("ROLE_SALES")))
                    .build());

            filter.filter(exchange, JwtAuthenticationFilterTest.this::chain).block();

            assertThat(forwarded).isNotNull();
            ServerHttpRequest request = forwarded.getRequest();
            // 各サービスは署名を再検証せず、ここで付けたクレームだけを見る（ADR-004）
            assertThat(request.getHeaders().getFirst(AuthenticatedUser.USER_ID_HEADER)).isEqualTo("sales01");
            assertThat(request.getHeaders().getFirst(AuthenticatedUser.ROLES_HEADER)).isEqualTo("ROLE_SALES");
        }

        @Test
        @DisplayName("利用者が名乗ったクレームヘッダは信用せず上書きする")
        void overwritesClientSuppliedIdentityHeaders() {
            MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/bookings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("sales01", List.of("ROLE_SALES")))
                    .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                    .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                    .build());

            filter.filter(exchange, JwtAuthenticationFilterTest.this::chain).block();

            assertThat(forwarded).isNotNull();
            // サービス側は署名を見ないため、ここで剥がさないと誰でも管理者を名乗れる
            assertThat(forwarded.getRequest().getHeaders().get(AuthenticatedUser.USER_ID_HEADER))
                    .containsExactly("sales01");
            assertThat(forwarded.getRequest().getHeaders().get(AuthenticatedUser.ROLES_HEADER))
                    .containsExactly("ROLE_SALES");
        }

        @Test
        @DisplayName("公開経路であってもクレームヘッダは剥がす")
        void stripsIdentityHeadersOnPublicPath() {
            MockServerWebExchange exchange =
                    exchange(MockServerHttpRequest.get("/api/v1/public/tracking/TRK-1")
                            .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN")
                            .build());

            filter.filter(exchange, JwtAuthenticationFilterTest.this::chain).block();

            assertThat(forwarded).isNotNull();
            assertThat(forwarded.getRequest().getHeaders().get(AuthenticatedUser.USER_ID_HEADER)).isNull();
            assertThat(forwarded.getRequest().getHeaders().get(AuthenticatedUser.ROLES_HEADER)).isNull();
        }
    }
}
