package com.example.gatewayms.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtAuthenticationFilterTest {

    private static final String SECRET = "ThisIsADefaultSecretKeyForDevelopmentPurposesOnly12345";

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void actuatorエンドポイントはトークンなしでアクセスできる() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void 認証必要パスにトークンなしでアクセスすると401を返す() {
        webTestClient.get()
                .uri("/api/routing/voyages")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void 不正なトークンでアクセスすると401を返す() {
        webTestClient.get()
                .uri("/api/routing/voyages")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void Bearer以外のAuthorizationヘッダーなら401を返す() {
        webTestClient.get()
                .uri("/api/routing/voyages")
                .header("Authorization", "Basic abcdef")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void 有効なトークンでアクセスするとフィルタを通過する() {
        String token = generateValidToken();

        webTestClient.get()
                .uri("/api/routing/voyages")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
                );
    }

    private String generateValidToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("testuser")
                .claim("roles", List.of("ROLE_ADMIN"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }
}
