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
            "@(?:Request|Get|Post|Put|Delete|Patch)Mapping\\s*\\([^)]*?\"(/[^\"]*)\"");

    /**
     * クラスに付いた {@code @RequestMapping}。メソッド側は
     * {@code @PutMapping("/{bookingId}")} のように<b>相対で書かれる</b>ので、
     * ここを前置きにしないと経路として復元できない。
     *
     * <p><b>これが無い間、検査は method 側の経路を 1 本も見ていなかった。</b>
     * {@code /api/} を含む文字列だけを拾っていたため、相対のものは「存在しない
     * こと」になり、ルートにも宣言にも無いまま緑になる。</p>
     */
    private static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\([^)]*?\"(/api/[^\"]+)\"");
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
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher classMapping = CLASS_MAPPING.matcher(source);
                String prefix = classMapping.find() ? classMapping.group(1) : null;
                Matcher matcher = ANY_MAPPING.matcher(source);
                while (matcher.find()) {
                    String path = matcher.group(1);
                    if (path.startsWith("/api/")) {
                        endpoints.add(path);
                    } else if (prefix != null) {
                        // 相対の経路を前置きと繋ぐ。"/" だけの指定は前置きそのもの。
                        endpoints.add("/".equals(path) ? prefix : prefix + path);
                    }
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
        // 相対で書かれた経路を拾えているか。これが 0 なら、method 側の注釈を
        // 1 本も見ていない（IT4 まで実際にそうだった）。
        assertThat(serviceEndpoints())
                .as("クラスの @RequestMapping とメソッドの相対経路を繋げている")
                .contains("/api/v1/booking/bookings/routing-worklist");
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
                        .noneMatch(pattern -> pattern.startsWith(literalPrefix(endpoint))))
                .toList();

        assertThat(unprotected)
                .as("公開してよい経路は PUBLIC_PATHS の 1 か所だけで決める")
                .isEmpty();
    }

    @Test
    @DisplayName("US01: 見積は営業だけ（荷主にも経路設計にも開かない）")
    void quotationsAreForSalesOnly() {
        List<String> sales = List.of("ROLE_SALES");
        String list = "/api/v1/booking/quotations";
        String one = list + "/Q-0123456789abcdef0123456789abcd";

        assertThat(RoleAuthorization.isAllowed("POST", list, sales))
                .as("営業が見積を作る").isTrue();
        assertThat(RoleAuthorization.isAllowed("GET", one, sales)).isTrue();

        assertThat(RoleAuthorization.isAllowed("GET", one, List.of("ROLE_SHIPPER")))
                .as("金額を出す荷主向けの画面は S62 だけ").isFalse();
        assertThat(RoleAuthorization.isAllowed("GET", one, List.of("ROLE_ROUTING")))
                .as("経路設計者は見積を読まない（経路の依頼は予約から来る）").isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", list, List.of("ROLE_ACCOUNTANT")))
                .as("経理は見積を作らない").isFalse();
    }

    @Test
    @DisplayName("S62 荷主向けの請求書は読み取りだけ（書き込みの経路を生やさない）")
    void shipperInvoicesAreReadOnly() throws IOException {
        // **書き込みの守りは「経路が無い」こと**で成り立つ。Gateway の名簿は
        // 全メソッドを宣言する規約なので、ここでロールを絞ることはできない。
        // 代わりに、荷主向けのコントローラに書き込みの割り当てが無いことを固定する。
        java.nio.file.Path controller = backendRoot().resolve(
                "billingms/src/main/java/com/example/cargotracker/billing/interfaces/rest/"
                        + "ShipperInvoiceController.java");
        assertThat(controller)
                .as("S62 のコントローラが実在する（名指しした検査が空振りしない）")
                .exists();

        String source = Files.readString(controller, StandardCharsets.UTF_8);
        assertThat(source)
                .as("荷主は自社の請求書を読むだけ。書き込みを足すなら、"
                        + "経理向けと同じくメソッド込みの宣言を先に置く")
                .doesNotContain("@PostMapping")
                .doesNotContain("@PutMapping")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("@PatchMapping");
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

    /**
     * 経路のうち<b>パス変数より手前</b>。{@code /a/b/{id}} なら {@code /a/b/}。
     *
     * <p>公開経路がパス変数を含むと、経路の文字列（{@code {trackingNumber}}）は
     * どの {@code PUBLIC_PATHS} にも前方一致しない。<b>比べるのは literal な部分</b>
     * ——「その経路のために書かれた公開宣言があるか」を見たいのであって、
     * 変数名の綴りを見たいのではない（IT8 T6 で最初の該当経路が出た）。</p>
     *
     * <p><b>緩めない。</b> 無関係な広いパターン（例: {@code /api/**}）でたまたま
     * 公開になっている経路は、literal な部分でも前方一致しないので今までどおり赤になる。</p>
     */
    private static String literalPrefix(String endpoint) {
        int variable = endpoint.indexOf('{');
        return variable < 0 ? endpoint : endpoint.substring(0, variable);
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
    @DisplayName("荷役の記録と取消は荷役ロールだけ（履歴は追跡も読める）")
    void handlingWritesAreForHandlersOnly() {
        // **IT9 の宣言はメソッドを見ていなかった。** 荷役履歴を
        // {荷役, 追跡} に開いたので、同じ経路への書き込みも追跡に開いていた。
        List<String> tracker = List.of("ROLE_TRACKER");
        List<String> handler = List.of("ROLE_HANDLER");
        String history = "/api/v1/handling/TRK-8K2QX7M4RB/activities";

        assertThat(RoleAuthorization.isAllowed("GET", history, tracker))
                .as("追跡管理者は問い合わせを受けたときに現場の記録を読む")
                .isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", history, tracker))
                .as("追跡管理者は荷役を記録しない")
                .isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", "/api/v1/handling/activities", handler))
                .as("荷役作業員は記録できる")
                .isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", "/api/v1/handling/activities", tracker))
                .as("追跡管理者は記録できない")
                .isFalse();
        assertThat(RoleAuthorization.isAllowed(
                        "POST", "/api/v1/handling/activities/act-1/void", handler))
                .as("荷役作業員は取り消せる")
                .isTrue();
        assertThat(RoleAuthorization.isAllowed(
                        "POST", "/api/v1/handling/activities/act-1/void", tracker))
                .as("追跡管理者は取り消せない")
                .isFalse();
    }

    @Test
    @DisplayName("US29: 通関の登録は荷役だけ、状態更新は追跡だけ（読みは両方）")
    void customsWritesAreSplitBetweenRoles() {
        // **メソッド込みで宣言し、そのロール以外が 403 になることを見る**（T8）。
        // 読みを {荷役, 追跡} に開いたので、書き込みの宣言を先に置かないと
        // 「一覧を読める側が登録も更新もできる」に落ちる（IT9 で踏んだ形）。
        List<String> handler = List.of("ROLE_HANDLER");
        List<String> tracker = List.of("ROLE_TRACKER");
        List<String> sales = List.of("ROLE_SALES");
        String list = "/api/v1/handling/customs-declarations";
        String status = "/api/v1/handling/customs-declarations/IMP-1/status";

        assertThat(RoleAuthorization.isAllowed("GET", list, handler))
                .as("荷役作業員は自分が出した申告の一覧を読む").isTrue();
        assertThat(RoleAuthorization.isAllowed("GET", list, tracker))
                .as("追跡管理者は督促の対象を読む").isTrue();
        assertThat(RoleAuthorization.isAllowed("GET", list, sales))
                .as("営業は通関に関わらない").isFalse();

        assertThat(RoleAuthorization.isAllowed("POST", list, handler))
                .as("申告を出すのは現場（荷役作業員）").isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", list, tracker))
                .as("追跡管理者は申告を出さない——読めるだけで書けてはいけない").isFalse();

        assertThat(RoleAuthorization.isAllowed("POST", status, tracker))
                .as("状態を更新するのは税関とのやりとりを追う側").isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", status, handler))
                .as("荷役作業員は状態を更新しない").isFalse();

        assertThat(RoleAuthorization.isAllowed(
                        "GET", "/api/v1/handling/customs-declarations/IMP-1/history", tracker))
                .as("履歴は状態を更新する側が読む").isTrue();
    }

    @Test
    @DisplayName("US21: 請求は経理だけが読み書きできる（肯定と否定の両方を見る）")
    void billingIsForAccountantsOnly() {
        // **メソッド込みで宣言し、そのロール以外が 403 になることを見る**（T8）。
        // IT10 の引き継ぎ枠 B で同じ穴を返済した形——読みの宣言に書き込みが
        // 吸われると、読める側が書けてしまう。
        List<String> accountant = List.of("ROLE_ACCOUNTANT");
        List<String> tracker = List.of("ROLE_TRACKER");
        List<String> shipper = List.of("ROLE_SHIPPER");
        List<String> sales = List.of("ROLE_SALES");
        String list = "/api/v1/billing/invoices";
        String one = "/api/v1/billing/invoices/INV-20260928-1a2b3c4d";
        String adjust = one + "/adjustments";

        assertThat(RoleAuthorization.isAllowed("GET", list, accountant))
                .as("経理は請求一覧（S60）を読む").isTrue();
        assertThat(RoleAuthorization.isAllowed("GET", one, accountant)).isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", adjust, accountant))
                .as("調整を入れるのは経理（US21 §6）").isTrue();

        assertThat(RoleAuthorization.isAllowed("GET", list, tracker))
                .as("追跡管理者は請求に関わらない").isFalse();
        assertThat(RoleAuthorization.isAllowed("GET", one, sales))
                .as("営業も請求は読まない").isFalse();

        // IT14 引き継ぎ B・C。**書き込みはメソッド込みで宣言する**——読み向けの
        // 広い宣言に吸われると、載せ忘れた書き込みほど無防備になる。
        String reversal = adjust + "/ADJ-1/reversal";
        String recalculate = list + "/recalculate";
        assertThat(RoleAuthorization.isAllowed("POST", reversal, accountant))
                .as("調整を取り消すのは経理").isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", reversal, sales))
                .as("営業は調整を取り消さない").isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", recalculate, accountant))
                .as("請求を作り直すのは経理").isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", recalculate, tracker))
                .as("追跡管理者は請求を作らない").isFalse();

        // US23 の書き込み（発行・入金・取消）。
        for (String write : new String[] {"/issue", "/payments", "/void"}) {
            assertThat(RoleAuthorization.isAllowed("POST", one + write, accountant))
                    .as("%s は経理", write).isTrue();
            assertThat(RoleAuthorization.isAllowed("POST", one + write, shipper))
                    .as("荷主は %s を叩けない", write).isFalse();
        }

        // S62 荷主向けの請求書（US23 §2）。**金額を出す唯一の荷主向け画面**で、
        // 自社のぶんだけを billingms が X-Auth-Shipper-Id で絞る。
        String shipperInvoice = "/api/v1/billing/shipper-invoices/INV-20260928-1a2b3c4d";
        assertThat(RoleAuthorization.isAllowed("GET", shipperInvoice, shipper))
                .as("荷主は自社の請求書を読む").isTrue();
        assertThat(RoleAuthorization.isAllowed("GET", shipperInvoice, accountant))
                .as("経理は経理向けの一覧から読む（同じ経路に分岐を足さない）").isFalse();
        // **書き込みの守りは「経路が無い」こと**で成り立つ。ここでロールを
        // 絞ると、名簿の規約（全メソッドを宣言する）と食い違う。経路が生えて
        // いないことは ShipperInvoiceIsReadOnlyTest が固定する。

        // IT14 引き継ぎ A。**確認済は担当ロールをサービス側が確かめる**ので、
        // Gateway は認証済みなら通す（一覧に出す条件と同じ条件を更新にも置く）。
        for (String service : new String[] {"booking", "routing", "billing"}) {
            String acknowledge = "/api/v1/" + service + "/attention-items/i-1/acknowledge";
            assertThat(RoleAuthorization.isDeclared("POST", acknowledge))
                    .as("%s の確認済に宣言がある（名簿に無い経路は通さない）", service)
                    .isTrue();
            assertThat(RoleAuthorization.isAllowed("POST", acknowledge, sales))
                    .as("%s の自分宛を確認できる", service).isTrue();
        }

        // **経理は根拠を開ける**（IT13 のレビュー 高）。調整は例外・通関申告を
        // 根拠に指すので、開けないと「なぜこの減額か」を確かめられない。
        // **読みだけ**——起票・状態更新は各ロールの宣言が守る。
        assertThat(RoleAuthorization.isAllowed(
                        "GET", "/api/v1/tracking/trackings/exceptions", accountant))
                .as("調整の根拠になった例外を読む").isTrue();
        assertThat(RoleAuthorization.isAllowed(
                        "POST", "/api/v1/tracking/trackings/TRK-1/exceptions", accountant))
                .as("起票はしない").isFalse();
        assertThat(RoleAuthorization.isAllowed(
                        "GET", "/api/v1/handling/customs-declarations/IMP-1", accountant))
                .as("留置の保管料の根拠になった申告を読む").isTrue();
        assertThat(RoleAuthorization.isAllowed(
                        "POST", "/api/v1/handling/customs-declarations/IMP-1/status", accountant))
                .as("通関状態は更新しない").isFalse();

        // **経理は指された予約を開ける**（要確認一覧が算出できなかった予約を出す）。
        // **読みだけ**——修正・確定・発行は各ロールの宣言が守る。
        assertThat(RoleAuthorization.isAllowed("GET", "/api/v1/booking/bookings/B-1", accountant))
                .as("算出できなかった予約を開く").isTrue();
        assertThat(RoleAuthorization.isAllowed("PUT", "/api/v1/booking/bookings/B-1", accountant))
                .as("予約は書き換えない").isFalse();
        assertThat(RoleAuthorization.isAllowed(
                        "POST", "/api/v1/booking/bookings/B-1/confirmation", accountant))
                .as("確定もしない").isFalse();
        assertThat(RoleAuthorization.isAllowed("GET", one, shipper))
                .as("荷主向けの請求書は S62（US23・IT14）。いまは開かない").isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", adjust, tracker))
                .as("読めない側が書ける余地を作らない").isFalse();

        assertThat(RoleAuthorization.isDeclared("POST", adjust))
                .as("宣言が無ければ通さない（載せ忘れた書き込みほど無防備になる）")
                .isTrue();
    }

    @Test
    @DisplayName("例外の起票・対応・解決は追跡管理者だけ（一覧は読めるが荷主は書けない）")
    void exceptionWritesAreForTrackersOnly() {
        // **読みの宣言（TRACKER, SHIPPER）に書き込みが吸われない**ことを見る。
        // 吸われると、荷主が自分の貨物に例外を起票できてしまう。
        List<String> tracker = List.of("ROLE_TRACKER");
        List<String> shipper = List.of("ROLE_SHIPPER");
        String base = "/api/v1/tracking/trackings/TRK-8K2QX7M4RB/exceptions";

        assertThat(RoleAuthorization.isAllowed("GET",
                "/api/v1/tracking/trackings/exceptions", tracker))
                .as("追跡管理者は未解決の例外を読む").isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", base, tracker))
                .as("追跡管理者は起票できる").isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", base, shipper))
                .as("荷主は起票できない").isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", base + "/ex-1/response", shipper))
                .as("荷主は対応を始められない").isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", base + "/ex-1/resolution", shipper))
                .as("荷主は解決できない").isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", base + "/ex-1/notifications", shipper))
                .as("荷主は通知の記録を残せない").isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", base + "/ex-1/resolution", tracker))
                .as("追跡管理者は解決できる").isTrue();
    }

    @Test
    @DisplayName("管理者は例外一覧を読めるが、起票も解決もできない（US20 §3 / IT11）")
    void adminReadsExceptionsButDoesNotWrite() {
        // **緊急を知らせる先として一覧を開く**（送信基盤はスコープ外なので、
        // 読み口が「知らせた」の実体になる）。**書き込みは開かない**——
        // 対応するのは追跡管理者の仕事で、管理者が起票できると持ち場が混ざる。
        List<String> admin = List.of("ROLE_ADMIN");
        List<String> shipper = List.of("ROLE_SHIPPER");
        String base = "/api/v1/tracking/trackings/TRK-8K2QX7M4RB/exceptions";

        assertThat(RoleAuthorization.isAllowed("GET",
                "/api/v1/tracking/trackings/exceptions", admin))
                .as("管理者は未解決の例外を読む").isTrue();
        assertThat(RoleAuthorization.isAllowed("POST", base, admin))
                .as("管理者は起票できない").isFalse();
        assertThat(RoleAuthorization.isAllowed("POST", base + "/ex-1/resolution", admin))
                .as("管理者は解決できない").isFalse();
        assertThat(RoleAuthorization.isAllowed("GET",
                "/api/v1/tracking/trackings/exceptions", shipper))
                .as("荷主には開かない（他社の貨物の例外まで並ぶ）").isFalse();
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
