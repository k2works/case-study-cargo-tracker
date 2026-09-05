package com.example.cargotracker.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.gateway.infrastructure.config.JwtAuthenticationFilter;
import com.example.cargotracker.gateway.infrastructure.config.RoleAuthorization;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 後段サービスの API 経路は、Gateway のルートに載り、認証で守られている。
 *
 * <p><b>IT2 では足した経路が未検査でした（レビュー L3）。</b> 経路は増え続けるので、
 * 「足したときに検査する」ではなく「足りていないと赤になる」形にします。</p>
 *
 * <p><b>ルートに載っている経路だけを数えません。</b> 各サービスの
 * {@code @RequestMapping} を全部拾ってから、ルートに載っているかを見ます。載って
 * いるものだけを数える検査は、載せ忘れたものほど漏らします。</p>
 *
 * <p>公開してよい経路は {@link JwtAuthenticationFilter#PUBLIC_PATHS} の 1 か所だけで
 * 決めます。ここに無い経路が認証を通らずに届く形にはしません。</p>
 */
class EveryServiceEndpointIsRoutedAndProtectedTest {

    /**
     * {@code /api/} を含む文字列を持つマッピング注釈を<b>全部</b>拾う。
     *
     * <p>{@code @RequestMapping("...")} の形だけを拾うと、
     * {@code @RequestMapping(value = "...")} やクラスレベル注釈を持たない
     * {@code @GetMapping("/api/...")} は「存在しないこと」になり、
     * ルートに載っていなくても緑になる。対象になりうるものを全部拾ってから、
     * 書き方を見る。</p>
     */
    private static final Pattern ANY_MAPPING = Pattern.compile(
            "@(Request|Get|Post|Put|Delete|Patch)Mapping\\s*\\([^)]*?\"(/api/[^\"]+)\"");

    /**
     * クラスに付いた {@code @RequestMapping} と、その中のメソッドに付いた注釈を
     * 組み合わせるための、書き込みを表す注釈の名前。
     */
    private static final List<String> WRITING = List.of("Post", "Put", "Delete", "Patch");
    private static final Pattern ROUTE_PREDICATE =
            Pattern.compile("Path=(/api/[^\\]\\s,]+)");

    private static Path backendRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts が見つかりません");
    }

    private static List<String> serviceEndpoints() throws IOException {
        List<String> endpoints = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            for (Path file : paths
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    // 名前で絞らない。*Controller.java 以外に書かれた経路は
                    // 「存在しないこと」になり、検査を素通りする。
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList()) {
                Matcher matcher = ANY_MAPPING.matcher(
                        Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    endpoints.add(matcher.group(2));
                }
            }
        }
        return endpoints;
    }

    private static List<String> gatewayRoutes() throws IOException {
        String config = Files.readString(
                backendRoot().resolve("gatewayms/src/main/resources/application.yml"),
                StandardCharsets.UTF_8);
        List<String> routes = new ArrayList<>();
        Matcher matcher = ROUTE_PREDICATE.matcher(config);
        while (matcher.find()) {
            routes.add(matcher.group(1));
        }
        return routes;
    }

    /** {@code /api/v1/booking/**} の形の予測子に、その経路が当たるか。 */
    private static boolean isCoveredBy(String endpoint, String pattern) {
        String prefix = pattern.endsWith("/**")
                ? pattern.substring(0, pattern.length() - 2) : pattern + "/";
        return (endpoint + "/").startsWith(prefix);
    }

    @Test
    @DisplayName("検査する経路が実際にある（空振りしていない）")
    void thereAreEndpointsToCheck() throws IOException {
        assertThat(serviceEndpoints()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(gatewayRoutes()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("後段サービスの経路はすべて Gateway のルートに載っている")
    void everyEndpointIsRouted() throws IOException {
        List<String> routes = gatewayRoutes();
        List<String> unrouted = serviceEndpoints().stream()
                .filter(endpoint -> routes.stream().noneMatch(r -> isCoveredBy(endpoint, r)))
                .toList();

        assertThat(unrouted)
                .as("Gateway に載っていない経路は、外から届かないか、Gateway を通らずに届く")
                .isEmpty();
    }

    @Test
    @DisplayName("後段サービスの経路は認証で守られている（公開は明示した分だけ）")
    void everyEndpointIsProtected() throws IOException {
        // 除外リストをテスト側に持たない。持つと、次に公開経路が増えたとき
        // 検査を無効化する側に働く。公開してよいものは PUBLIC_PATHS が決める。
        List<String> unprotected = serviceEndpoints().stream()
                .filter(endpoint -> JwtAuthenticationFilter.isPublic(endpoint + "/x"))
                .filter(endpoint -> JwtAuthenticationFilter.PUBLIC_PATHS.stream()
                        .noneMatch(pattern -> pattern.startsWith(endpoint)))
                .toList();

        assertThat(unprotected)
                .as("公開してよい経路は PUBLIC_PATHS の 1 か所だけで決める")
                .isEmpty();
    }

    @Test
    @DisplayName("後段サービスの経路にはすべて要求ロールが宣言されている")
    void everyEndpointDeclaresRequiredRoles() throws IOException {
        // 認証だけでは足りない。宣言が無い経路は「認証済みなら誰でも」に
        // なってしまう（IT3 のレビューまで全経路がその状態だった）。
        //
        // **メソッドも見る（決定 6）。** 経路だけで見ると、書き込みを足したときに
        // 読み向けの広い宣言に当たって「宣言がある」と読める。
        List<String> undeclared = serviceEndpoints().stream()
                .filter(endpoint -> !JwtAuthenticationFilter.isPublic(endpoint + "/x"))
                .filter(endpoint -> METHODS.stream().anyMatch(method ->
                        !RoleAuthorization.isDeclared(method, endpoint)
                                || !RoleAuthorization.isDeclared(method, endpoint + "/x")))
                .toList();

        assertThat(undeclared)
                .as("要求ロールを宣言していない経路は、認証済みなら誰でも叩ける")
                .isEmpty();
    }

    /** 実在するメソッド。宣言はこの全部に対して要る。 */
    private static final List<String> METHODS =
            List.of("GET", "POST", "PUT", "DELETE", "PATCH");

    @Test
    @DisplayName("書き込みの経路は、書き込み用の宣言に当たる")
    void writingEndpointsAreDeclaredForTheirMethod() throws IOException {
        // 決定 6（宣言はメソッドも見る）を検査に落とす。書き込みが読み向けの
        // 広い宣言に吸われると、読める人が全員書けることになる。
        //
        // 予約の修正（PUT /bookings/{id}）は営業だけ。参照は経路設計・追跡にも
        // 開いているので、同じ経路でも答えが変わらなければならない。
        List<String> roleDesigner = List.of("ROLE_ROUTING");

        assertThat(RoleAuthorization.isAllowed("GET", "/api/v1/booking/bookings/b-1", roleDesigner))
                .as("経路設計者は予約を読める")
                .isTrue();
        assertThat(RoleAuthorization.isAllowed("PUT", "/api/v1/booking/bookings/b-1", roleDesigner))
                .as("経路設計者は予約を書き換えられない")
                .isFalse();
    }

    @Test
    @DisplayName("宣言が実際にある（検査が空振りしていない）")
    void thereAreRoleDeclarations() throws IOException {
        // 実数に近い下限にする。5 のままだと、宣言が減っても気づけない。
        assertThat(RoleAuthorization.declaredPatterns())
                .hasSizeGreaterThanOrEqualTo(serviceEndpoints().stream().distinct().toList()
                        .size() / 2);
    }
}
