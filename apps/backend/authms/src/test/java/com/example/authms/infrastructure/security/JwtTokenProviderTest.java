package com.example.authms.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider("ThisIsADefaultSecretKeyForDevelopmentPurposesOnly12345", 3600000);

    @Test
    void 有効なトークンを検証できる() {
        String token = jwtTokenProvider.generateToken("admin", List.of("ROLE_ADMIN"));

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("admin");
        assertThat(jwtTokenProvider.getRolesFromToken(token)).containsExactly("ROLE_ADMIN");
    }

    @Test
    void 不正なトークンは無効と判定する() {
        assertThat(jwtTokenProvider.validateToken("invalid-token")).isFalse();
    }
}
