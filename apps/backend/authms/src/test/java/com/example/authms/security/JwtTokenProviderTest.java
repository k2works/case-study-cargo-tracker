package com.example.authms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JwtTokenProvider} のユニットテスト。
 *
 * <p>HS256 互換の十分長い秘密鍵で署名・検証・期限処理を網羅する。</p>
 */
class JwtTokenProviderTest {

    // 32 バイト以上の秘密鍵（HS256 要件）
    private static final String SECRET = "test-secret-key-with-enough-length-for-hs256-12345678";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 60_000L);
    }

    @Test
    @DisplayName("ユーザー名とロールを含む JWT を生成し検証できる")
    void トークン生成と検証() {
        String token = provider.generateToken("alice", "ROLE_ADMIN");

        assertThat(token).isNotBlank();
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsername(token)).isEqualTo("alice");
        assertThat(provider.getRole(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("不正なトークン文字列は validateToken が false を返す")
    void 不正トークンはfalse() {
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("別の秘密鍵で署名された JWT は検証に失敗する")
    void 異なる鍵のトークンは検証失敗() {
        JwtTokenProvider other = new JwtTokenProvider(
                "OTHER-secret-key-with-enough-length-for-hs256-1234", 60_000L);
        String token = other.generateToken("bob", "ROLE_SALES");

        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("期限切れトークンは validateToken が false を返す")
    void 期限切れはfalse() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -1L);
        String token = shortLived.generateToken("eve", "ROLE_HANDLER");

        assertThat(provider.validateToken(token)).isFalse();
    }
}
