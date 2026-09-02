package com.example.cargotracker.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.gateway.infrastructure.config.JwtAuthenticationFilter;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Gateway の認証（US26 タスク 4.4）。
 *
 * <p>守りを足したときに壊れやすいのは「守らなくてよい経路」のほう。公開追跡が
 * 401 になれば荷受人が使えなくなり、ヘルスチェックが 401 になれば再起動ループに入る。
 * 通ることも検査する。</p>
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "cargo-tracker-development-secret-key-32bytes!";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);

    private static String validToken() {
        return Jwts.builder()
                .subject("sales01")
                .claim("roles", List.of("ROLE_SALES"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .compact();
    }

    private MockHttpServletResponse run(String uri, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, Mockito.mock(FilterChain.class));
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/auth/login",
        "/api/v1/tracking/public/ABC123",
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/info",
    })
    @DisplayName("公開経路は資格情報が無くても 401 にしない")
    void allowsPublicPaths(String uri) throws Exception {
        assertThat(run(uri, null).getStatus())
                .as("%s を守ると、荷受人が使えなくなるか再起動ループに入る", uri)
                .isNotEqualTo(401);
    }

    @Test
    @DisplayName("保護経路は資格情報が無ければ 401")
    void rejectsProtectedPathWithoutToken() throws Exception {
        assertThat(run("/api/v1/booking/shippers", null).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("正しい JWT なら通す")
    void allowsValidToken() throws Exception {
        assertThat(run("/api/v1/booking/shippers", "Bearer " + validToken()).getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("署名が違う JWT は 401")
    void rejectsWrongSignature() throws Exception {
        String forged = Jwts.builder().subject("attacker")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(new SecretKeySpec(
                        "another-secret-key-that-is-32-bytes-long!".getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256"))
                .compact();

        assertThat(run("/api/v1/booking/shippers", "Bearer " + forged).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("期限切れの JWT は 401")
    void rejectsExpiredToken() throws Exception {
        String expired = Jwts.builder().subject("sales01")
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .compact();

        assertThat(run("/api/v1/booking/shippers", "Bearer " + expired).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Bearer でない Authorization は 401")
    void rejectsNonBearer() throws Exception {
        assertThat(run("/api/v1/booking/shippers", "Basic dXNlcjpwYXNz").getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("401 の本文は理由を言わない")
    void doesNotRevealReason() throws Exception {
        String body = run("/api/v1/booking/shippers", null).getContentAsString();

        assertThat(body).isEqualTo("{\"code\":\"UNAUTHENTICATED\"}");
    }

    @Test
    @DisplayName("公開経路の名簿は前方一致で他の経路まで開けない")
    void publicPathsDoNotLeak() {
        assertThat(JwtAuthenticationFilter.isPublic("/api/v1/tracking/internal/ABC123"))
                .as("public を含まない追跡経路まで開けてはいけない")
                .isFalse();
        assertThat(JwtAuthenticationFilter.isPublic("/api/v1/auth/login/../booking/shippers"))
                .isFalse();
    }
}
