package com.example.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 利用者ヘッダの検査（ADR-007）。
 *
 * <p>この検査が守るのは「認可を書き忘れた 1 本が無認証で開く」ことである。ロール検査を書いた
 * 画面は守られるが、書き忘れたエンドポイントはヘッダが無くても素通りしてしまう。
 */
@DisplayName("利用者ヘッダの検査")
class AuthenticatedUserFilterTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private static MockHttpServletRequest requestTo(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    private static boolean passedThrough(FilterChain chain) {
        return ((MockFilterChain) chain).getRequest() != null;
    }

    @Nested
    @DisplayName("業務のリクエスト")
    class BusinessRequests {

        @Test
        @DisplayName("ヘッダがあれば通し、解決した利用者を後続へ渡す")
        void passesWithHeaders() throws ServletException, IOException {
            MockHttpServletRequest request = requestTo("/api/v1/bookings");
            request.addHeader(AuthenticatedUser.USER_ID_HEADER, "sales01");
            request.addHeader(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES");
            MockFilterChain chain = new MockFilterChain();

            new AuthenticatedUserFilter().doFilter(request, response, chain);

            assertThat(passedThrough(chain)).isTrue();
            AuthenticatedUser user =
                    (AuthenticatedUser) request.getAttribute(AuthenticatedUserFilter.ATTRIBUTE);
            assertThat(user.userId()).isEqualTo("sales01");
            assertThat(user.hasAnyRole(Role.ROLE_SALES)).isTrue();
        }

        @Test
        @DisplayName("ヘッダが無いリクエストは 401 で止める")
        void rejectsWithoutHeaders() throws ServletException, IOException {
            MockFilterChain chain = new MockFilterChain();

            new AuthenticatedUserFilter().doFilter(requestTo("/api/v1/bookings"), response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(passedThrough(chain)).isFalse();
        }

        @Test
        @DisplayName("空の利用者 ID も通さない")
        void rejectsBlankUserId() throws ServletException, IOException {
            MockHttpServletRequest request = requestTo("/api/v1/bookings");
            request.addHeader(AuthenticatedUser.USER_ID_HEADER, "  ");
            MockFilterChain chain = new MockFilterChain();

            new AuthenticatedUserFilter().doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(passedThrough(chain)).isFalse();
        }

        /**
         * 拒否の理由は返さない。
         *
         * <p>「ヘッダが無い」と教えることは、Gateway を迂回できたことを攻撃者に確認させる。
         */
        @Test
        @DisplayName("拒否の応答に、迂回できたかどうかの手がかりを出さない")
        void doesNotLeakTheReason() throws ServletException, IOException {
            new AuthenticatedUserFilter()
                    .doFilter(requestTo("/api/v1/bookings"), response, new MockFilterChain());

            assertThat(response.getContentAsString())
                    .isEqualTo("{\"message\":\"認証が必要です\"}")
                    .doesNotContain(AuthenticatedUser.USER_ID_HEADER);
            assertThat(response.getContentType()).contains("application/json");
        }

        @Test
        @DisplayName("ロールが無くても、利用者が分かれば通す（認可は各サービスが判断する）")
        void passesWithoutRoles() throws ServletException, IOException {
            MockHttpServletRequest request = requestTo("/api/v1/bookings");
            request.addHeader(AuthenticatedUser.USER_ID_HEADER, "someone");
            MockFilterChain chain = new MockFilterChain();

            new AuthenticatedUserFilter().doFilter(request, response, chain);

            assertThat(passedThrough(chain)).isTrue();
            AuthenticatedUser user =
                    (AuthenticatedUser) request.getAttribute(AuthenticatedUserFilter.ATTRIBUTE);
            assertThat(user.roles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("除外するパス")
    class OpenPaths {

        /**
         * ヘルスチェックは必ず通す。
         *
         * <p>一律に適用すると、Kubernetes の liveness / readiness が 401 を受けて再起動ループに
         * 入る（IT1 で Gateway の JWT フィルタが同じ形で失敗した）。サービスの裁量にもしない。
         * 裁量にすると、除外を書き忘れたサービスだけが落ちる。
         */
        @Test
        @DisplayName("ヘルスチェックはヘッダ無しでも通す")
        void alwaysPassesHealthProbes() throws ServletException, IOException {
            for (String path : List.of("/actuator/health", "/actuator/health/liveness",
                    "/actuator/health/readiness")) {
                MockFilterChain chain = new MockFilterChain();

                new AuthenticatedUserFilter().doFilter(requestTo(path),
                        new MockHttpServletResponse(), chain);

                assertThat(passedThrough(chain)).as(path).isTrue();
            }
        }

        @Test
        @DisplayName("登録した公開パスはヘッダ無しでも通す")
        void passesDeclaredOpenPaths() throws ServletException, IOException {
            MockFilterChain chain = new MockFilterChain();

            new AuthenticatedUserFilter(List.of("/api/v1/auth"))
                    .doFilter(requestTo("/api/v1/auth/login"), response, chain);

            assertThat(passedThrough(chain)).isTrue();
        }

        /** 公開するのは宣言した接頭辞だけ。既定は閉じたままにする。 */
        @Test
        @DisplayName("公開パスを宣言しても、他の道は開かない")
        void doesNotOpenEverythingElse() throws ServletException, IOException {
            MockFilterChain chain = new MockFilterChain();

            new AuthenticatedUserFilter(List.of("/api/v1/auth"))
                    .doFilter(requestTo("/api/v1/users"), response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(passedThrough(chain)).isFalse();
        }

        @Test
        @DisplayName("ヘルスチェック以外の actuator は守る")
        void protectsOtherActuatorEndpoints() throws ServletException, IOException {
            MockFilterChain chain = new MockFilterChain();

            new AuthenticatedUserFilter().doFilter(requestTo("/actuator/metrics"), response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(passedThrough(chain)).isFalse();
        }
    }
}
