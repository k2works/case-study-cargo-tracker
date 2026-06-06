package com.example.gatewayms.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthenticationFilter} の単体テスト（IT9 A3.3 / US28）。
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-jwt-secret-32bytes-or-longer-aaaaa";
    private final SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setup() {
        filter = new JwtAuthenticationFilter(SECRET);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void 公開パスは認証なしでチェーンを通過する() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/login").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void Stripe_Webhook_パスは公開パスとしてチェーンを通過する() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/billing/webhooks/stripe").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    void Authorization_ヘッダ欠落は認証失敗応答を返す() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/bookings").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void 不正な_JWT_は認証失敗応答を返す() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt-token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void 有効な_JWT_は検証成功して転送ヘッダを付与する() {
        String token = generateToken("user1", "ROLE_SALES");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(argThat(ex -> {
            String user = ex.getRequest().getHeaders().getFirst("X-Forwarded-User");
            String role = ex.getRequest().getHeaders().getFirst("X-Forwarded-Role");
            return "user1".equals(user) && "ROLE_SALES".equals(role);
        }));
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void 短すぎるシークレットは初期化失敗する() {
        assertThatThrownBy(() -> new JwtAuthenticationFilter("too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 バイト以上");
    }

    private String generateToken(String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(secretKey)
                .compact();
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
