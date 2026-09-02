package com.example.gatewayms.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

@DisplayName("公開経路の判定")
class PublicPathMatcherTest {

    /** 実際に運用する設定（application.yml の app.public-paths）と同じ内容。 */
    private final PublicPathMatcher matcher = new PublicPathMatcher(List.of(
            new PublicPath(HttpMethod.GET, "/api/v1/public/tracking/*"),
            new PublicPath(HttpMethod.POST, "/api/v1/auth/login")));

    @Nested
    @DisplayName("許可する経路")
    class Allowed {

        @Test
        @DisplayName("追跡照会は認証なしで通す")
        void allowsPublicTracking() {
            assertThat(matcher.isPublic(HttpMethod.GET, "/api/v1/public/tracking/TRK-20260819-1234")).isTrue();
        }

        @Test
        @DisplayName("ログインは認証なしで通す（そうしないと誰もログインできない）")
        void allowsLogin() {
            assertThat(matcher.isPublic(HttpMethod.POST, "/api/v1/auth/login")).isTrue();
        }
    }

    @Nested
    @DisplayName("素通りさせない")
    class NotAllowed {

        /**
         * 名簿方式の検査は「載っていないもの」を通すと、載せ忘れたものほど漏れる。
         * public-paths のパターンが広すぎて業務 API まで公開されていないことを、
         * 主要な業務経路を名指しで確認する。
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "/api/v1/bookings",
            "/api/v1/bookings/BK-0001",
            "/api/v1/shippers",
            "/api/v1/voyages",
            "/api/v1/routes/optimal",
            "/api/v1/handling",
            "/api/v1/customs",
            "/api/v1/billing",
            "/api/v1/tracking/manage",
            "/api/v1/tracking/TRK-1/status"
        })
        @DisplayName("業務 API は GET でも認証を要求する")
        void requiresAuthenticationForBusinessApis(String path) {
            assertThat(matcher.isPublic(HttpMethod.GET, path))
                    .as("%s が認証なしで通っている", path)
                    .isFalse();
        }

        @Test
        @DisplayName("追跡照会は参照のみ公開する（更新は認証を要求する）")
        void doesNotAllowWritesOnPublicPath() {
            assertThat(matcher.isPublic(HttpMethod.POST, "/api/v1/public/tracking/TRK-1")).isFalse();
            assertThat(matcher.isPublic(HttpMethod.DELETE, "/api/v1/public/tracking/TRK-1")).isFalse();
        }

        @Test
        @DisplayName("ログイン以外の認証 API は公開しない")
        void doesNotAllowOtherAuthApis() {
            assertThat(matcher.isPublic(HttpMethod.POST, "/api/v1/auth/logout")).isFalse();
            // パスの前方一致だけで判定すると /api/v1/auth/login-as のような経路が漏れる
            assertThat(matcher.isPublic(HttpMethod.POST, "/api/v1/auth/loginAsAdmin")).isFalse();
        }

        @Test
        @DisplayName("一覧に無い経路は既定で認証を要求する")
        void requiresAuthenticationForUnknownPath() {
            assertThat(matcher.isPublic(HttpMethod.GET, "/api/v1/anything-new")).isFalse();
        }
    }
}
