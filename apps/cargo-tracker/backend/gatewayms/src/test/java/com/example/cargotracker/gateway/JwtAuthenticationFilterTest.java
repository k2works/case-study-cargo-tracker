package com.example.cargotracker.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.gateway.infrastructure.config.JwtAuthenticationFilter;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
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
        return run(uri, authorization, Mockito.mock(FilterChain.class), java.util.Map.of());
    }

    /** メソッドで許すロールが変わる経路（US32）を踏むため。 */
    private MockHttpServletResponse run(String uri, String authorization, String method)
            throws Exception {
        return run(uri, authorization, Mockito.mock(FilterChain.class), java.util.Map.of(),
                method);
    }

    private MockHttpServletResponse run(String uri, String authorization, FilterChain chain,
            java.util.Map<String, String> extraHeaders) throws Exception {
        return run(uri, authorization, chain, extraHeaders, "GET");
    }

    private MockHttpServletResponse run(String uri, String authorization, FilterChain chain,
            java.util.Map<String, String> extraHeaders, String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        extraHeaders.forEach(request::addHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    /** フィルタが後段へ渡した要求を捕まえる。 */
    private HttpServletRequest forwarded(String authorization,
            java.util.Map<String, String> extraHeaders) throws Exception {
        return forwarded("/api/v1/booking/shippers", authorization, extraHeaders);
    }

    /**
     * 経路を選べる形。**認可が経路ごとに効く**ので、ロールに許されていない経路を
     * 使うと後段まで届かず、身元の伝達そのものを確かめられない。
     */
    private HttpServletRequest forwarded(String uri, String authorization,
            java.util.Map<String, String> extraHeaders) throws Exception {
        var captured = new java.util.concurrent.atomic.AtomicReference<HttpServletRequest>();
        FilterChain chain = (req, res) -> captured.set((HttpServletRequest) req);
        run(uri, authorization, chain, extraHeaders);
        return captured.get();
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
    @DisplayName("利用者とロールをヘッダで後段へ伝える")
    void propagatesIdentityAsHeaders() throws Exception {
        HttpServletRequest forwarded = forwarded("Bearer " + validToken(), java.util.Map.of());

        // 属性でなくヘッダに載せる。属性は Gateway の JVM 内に閉じており、
        // 後段へ HTTP 転送されない（後段は何も受け取れない）。
        assertThat(forwarded.getHeader(JwtAuthenticationFilter.USERNAME_HEADER))
                .isEqualTo("sales01");
        assertThat(forwarded.getHeader(JwtAuthenticationFilter.ROLES_HEADER))
                .isEqualTo("ROLE_SALES");
        assertThat(java.util.Collections.list(forwarded.getHeaderNames()))
                .contains(JwtAuthenticationFilter.USERNAME_HEADER,
                        JwtAuthenticationFilter.ROLES_HEADER);
    }

    @Test
    @DisplayName("外から付けられた身元ヘッダは上書きする（権限の偽装を通さない）")
    void overwritesSpoofedIdentityHeaders() throws Exception {
        HttpServletRequest forwarded = forwarded("Bearer " + validToken(), java.util.Map.of(
                JwtAuthenticationFilter.USERNAME_HEADER, "attacker",
                JwtAuthenticationFilter.ROLES_HEADER, "ROLE_ADMIN"));

        assertThat(forwarded.getHeader(JwtAuthenticationFilter.ROLES_HEADER))
                .as("上書きしないと、利用者が自分でヘッダを付けるだけで権限を偽れる")
                .isEqualTo("ROLE_SALES");
        assertThat(forwarded.getHeader(JwtAuthenticationFilter.USERNAME_HEADER))
                .isEqualTo("sales01");
    }

    @Test
    @DisplayName("権限の無いロールは後段へ届かず 403 になる")
    void rejectsCallerWithoutRequiredRole() throws Exception {
        // 画面の RequireRole は守りではない。ブラウザを介さずに叩けば素通りする。
        // IT3 のレビューまで、認証さえ通れば誰でも航海を登録できた。
        String salesToken = tokenWithRoles("ROLE_SALES");

        assertThat(run("/api/v1/routing/voyages", "Bearer " + salesToken).getStatus())
                .as("営業が航海を登録できてはいけない")
                .isEqualTo(403);
        assertThat(run("/api/v1/booking/bookings/routing-worklist", "Bearer " + salesToken)
                        .getStatus())
                .as("経路設計の作業一覧は全荷主の予約が見える")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("予約の修正は営業だけに開く（US32）")
    void restrictsBookingUpdateToSales() throws Exception {
        // /bookings/** は営業・経路設計・追跡に開いている。修正はそのうち営業だけ。
        // 細かい経路を先に置かないと、広いほうに吸われて経路設計者も直せる。
        String bookingPath = "/api/v1/booking/bookings/b-1";
        assertThat(run(bookingPath, "Bearer " + tokenWithRoles("ROLE_ROUTING"), "PUT").getStatus())
                .as("経路設計者が予約の内容を直せてはいけない")
                .isEqualTo(403);
        assertThat(run(bookingPath, "Bearer " + tokenWithRoles("ROLE_TRACKER"), "PUT").getStatus())
                .isEqualTo(403);
        assertThat(run(bookingPath, "Bearer " + tokenWithRoles("ROLE_SALES"), "PUT").getStatus())
                .as("営業は直せる")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("経路候補の算出と経路の確定は経路設計者だけに開く（US08・US09）")
    void restrictsRouteDesignToRoutingRole() throws Exception {
        // GET /bookings/*/route-candidates は既存の広い宣言（/bookings/**）と
        // **同じメソッド**なので、順序でしか絞れない。後ろに置くと営業・追跡にも
        // 開いたままになり、この検査だけが緑になる。
        String candidates = "/api/v1/booking/bookings/b-1/route-candidates";
        assertThat(run(candidates, "Bearer " + tokenWithRoles("ROLE_SALES")).getStatus())
                .as("営業が経路候補を算出できてはいけない")
                .isEqualTo(403);
        assertThat(run(candidates, "Bearer " + tokenWithRoles("ROLE_TRACKER")).getStatus())
                .isEqualTo(403);
        assertThat(run(candidates, "Bearer " + tokenWithRoles("ROLE_ROUTING")).getStatus())
                .as("経路設計者は算出できる")
                .isNotEqualTo(403);

        String assign = "/api/v1/booking/bookings/b-1/route";
        assertThat(run(assign, "Bearer " + tokenWithRoles("ROLE_SALES"), "POST").getStatus())
                .as("営業が経路を確定できてはいけない")
                .isEqualTo(403);
        assertThat(run(assign, "Bearer " + tokenWithRoles("ROLE_TRACKER"), "POST").getStatus())
                .isEqualTo(403);
        assertThat(run(assign, "Bearer " + tokenWithRoles("ROLE_ROUTING"), "POST").getStatus())
                .as("経路設計者は確定できる")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("航海を止める前の影響範囲は経路設計者だけに開く（S34 / US24）")
    void restrictsAffectedBookingsToRoutingRole() throws Exception {
        // 予約番号と状態を並べて返すので、広い宣言（/bookings/**）に吸われると
        // 営業・追跡が全予約を航海単位で引ける読み口になる。
        String affected = "/api/v1/booking/bookings/by-voyage/V-MOL-001";
        assertThat(run(affected, "Bearer " + tokenWithRoles("ROLE_SALES")).getStatus())
                .as("営業が航海の影響範囲を引けてはいけない")
                .isEqualTo(403);
        assertThat(run(affected, "Bearer " + tokenWithRoles("ROLE_TRACKER")).getStatus())
                .isEqualTo(403);
        assertThat(run(affected, "Bearer " + tokenWithRoles("ROLE_ROUTING")).getStatus())
                .as("経路設計者は引ける")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("予約の参照は営業以外にも開いたままにする")
    void keepsBookingReadOpen() throws Exception {
        // 修正を絞ったついでに参照まで絞ると、経路設計者が予約の詳細を開けなくなる。
        assertThat(run("/api/v1/booking/bookings/b-1", "Bearer " + tokenWithRoles("ROLE_ROUTING"))
                        .getStatus())
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("宣言されたロールを持っていれば後段へ届く")
    void allowsCallerWithRequiredRole() throws Exception {
        assertThat(run("/api/v1/routing/voyages", "Bearer " + tokenWithRoles("ROLE_ROUTING"))
                        .getStatus())
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("宣言の無い経路は通さない（載せ忘れを素通りさせない）")
    void rejectsUndeclaredPath() throws Exception {
        // 「載っていないものを許す」形にすると、載せ忘れた経路ほど無防備になる。
        assertThat(run("/api/v1/booking/unknown-resource", "Bearer " + validToken()).getStatus())
                .isEqualTo(403);
    }

    private String tokenWithRoles(String... roles) {
        return Jwts.builder().subject("tester")
                .claim("roles", List.of(roles))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .compact();
    }

    @Test
    @DisplayName("荷主なら荷主 ID も伝える。荷主でなければ伝えない")
    void propagatesShipperIdOnlyWhenPresent() throws Exception {
        String shipperToken = Jwts.builder().subject("shipper01")
                .claim("roles", List.of("ROLE_SHIPPER"))
                .claim("shipperId", "SHP-000001")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .compact();

        // 荷主は荷主一覧を開けないので、荷主にも許された経路で確かめる。
        HttpServletRequest withShipper = forwarded("/api/v1/booking/attention-items",
                "Bearer " + shipperToken, java.util.Map.of());
        assertThat(withShipper.getHeader(JwtAuthenticationFilter.SHIPPER_ID_HEADER))
                .isEqualTo("SHP-000001");

        HttpServletRequest withoutShipper = forwarded("Bearer " + validToken(), java.util.Map.of());
        assertThat(withoutShipper.getHeader(JwtAuthenticationFilter.SHIPPER_ID_HEADER))
                .as("荷主でないのに荷主 ID があると、絞り込みが誤って効く")
                .isNull();
        assertThat(java.util.Collections.list(withoutShipper.getHeaderNames()))
                .doesNotContain(JwtAuthenticationFilter.SHIPPER_ID_HEADER);
    }

    @Test
    @DisplayName("身元以外のヘッダはそのまま後段へ通す")
    void passesOtherHeadersThrough() throws Exception {
        HttpServletRequest forwarded = forwarded("Bearer " + validToken(),
                java.util.Map.of("X-Request-Id", "req-1"));

        assertThat(forwarded.getHeader("X-Request-Id")).isEqualTo("req-1");
        assertThat(java.util.Collections.list(forwarded.getHeaders("X-Request-Id")))
                .containsExactly("req-1");
        assertThat(java.util.Collections.list(
                forwarded.getHeaders(JwtAuthenticationFilter.ROLES_HEADER)))
                .containsExactly("ROLE_SALES");
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

    /**
     * 保護されるべき経路。**IT で足した経路をここに書き足す。**
     *
     * <p>1 本だけ見ていると、あとから足した経路が {@code PUBLIC_PATHS} に紛れ込んでも
     * 気づけない。とくに {@code /api/v1/auth/**} をまとめて公開にすると、
     * {@code X-Auth-Roles: ROLE_ADMIN} を自分で付けるだけで誰でもロックを解除できる
     * （ヘッダの上書きは JWT を通った要求にしか働かない）。</p>
     */
    private static final java.util.List<String> PROTECTED_PATHS = java.util.List.of(
            "/api/v1/booking/shippers",
            "/api/v1/booking/bookings",
            "/api/v1/auth/admin/users",
            "/api/v1/auth/admin/users/sales01/unlock",
            "/api/v1/booking/attention-items");

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("protectedPaths")
    @DisplayName("保護経路は公開扱いにならない")
    void protectedPathsAreNotPublic(String path) {
        assertThat(JwtAuthenticationFilter.isPublic(path))
                .as("%s が公開扱いになると、認証なしで通る", path)
                .isFalse();
    }

    static java.util.stream.Stream<String> protectedPaths() {
        return PROTECTED_PATHS.stream();
    }
}
