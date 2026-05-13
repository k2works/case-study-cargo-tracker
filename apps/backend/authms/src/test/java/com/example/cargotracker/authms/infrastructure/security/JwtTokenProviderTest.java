package com.example.cargotracker.authms.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
                "test-secret-key-at-least-32-characters-long",
                86400000L
        );
    }

    @Test
    @DisplayName("ユーザー名から JWT を生成できる")
    void ユーザー名からJWTを生成できる() {
        String token = provider.generateToken("alice");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("生成したトークンからユーザー名を取得できる")
    void 生成したトークンからユーザー名を取得できる() {
        String token = provider.generateToken("alice");
        assertThat(provider.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("有効なトークンは検証を通過する")
    void 有効なトークンは検証を通過する() {
        String token = provider.generateToken("alice");
        assertThat(provider.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("改ざんしたトークンは無効と判定される")
    void 改ざんしたトークンは無効と判定される() {
        assertThat(provider.isTokenValid("invalid.token.here")).isFalse();
    }
}
