package com.example.gatewayms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;

/**
 * 設定（application.yml）とフィルタの配線を検証する。
 *
 * <p>フィルタ単体が正しくても、public-paths のバインドに失敗して一覧が空になれば
 * すべての経路が保護され、逆に読み違えれば業務 API が公開される。設定を通した形で確認する。
 */
@SpringBootTest
@DisplayName("Gateway の認証設定")
class GatewayAuthenticationTest {

    @Autowired
    private PublicPathMatcher publicPathMatcher;

    @Autowired
    private GatewaySecurityProperties properties;

    @Test
    @DisplayName("設定した公開経路が読み込まれている")
    void loadsConfiguredPublicPaths() {
        assertThat(properties.toPublicPaths()).isNotEmpty();
        assertThat(publicPathMatcher.isPublic(HttpMethod.POST, "/api/v1/auth/login")).isTrue();
        assertThat(publicPathMatcher.isPublic(HttpMethod.GET, "/api/v1/public/tracking/TRK-1")).isTrue();
    }

    @Test
    @DisplayName("実際の設定でも業務 API は認証を要求する")
    void protectsBusinessApisWithRealConfiguration() {
        List<String> businessPaths = List.of(
                "/api/v1/bookings",
                "/api/v1/shippers",
                "/api/v1/tracking/manage",
                "/api/v1/customs",
                "/api/v1/billing",
                "/api/v1/auth/me");

        for (String path : businessPaths) {
            assertThat(publicPathMatcher.isPublic(HttpMethod.GET, path))
                    .as("%s が認証なしで通っている", path)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("JWT の署名鍵が設定されている")
    void hasJwtSecret() {
        assertThat(properties.jwt().secret()).isNotBlank();
    }
}
