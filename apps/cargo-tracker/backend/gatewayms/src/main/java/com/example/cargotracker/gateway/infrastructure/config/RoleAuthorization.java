package com.example.cargotracker.gateway.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.AntPathMatcher;

/**
 * 経路ごとに、その操作を許すロールを宣言する。
 *
 * <p><b>認可の正典はここ 1 か所である。</b> 画面側の {@code RequireRole} はナビと
 * 誤操作を減らすためのもので、守りではない。ブラウザを介さずに叩けば素通りする。
 * IT3 のレビューまで、サーバ側にロールの検査は 1 つも無く、認証さえ通れば
 * 誰でも航海を登録し、全荷主の予約一覧を読めた。</p>
 *
 * <p><b>名簿に無い経路は通さない。</b> 「載っていないものを許す」形にすると、
 * 載せ忘れた経路ほど無防備になる。宣言の抜けは
 * {@code EveryServiceEndpointIsRoutedAndProtectedTest} が赤にする。</p>
 *
 * <p>表示ロールの正典は {@code ui_design.md} の画面一覧である。ここはその画面が
 * 使う API に翻訳したもので、画面より広くしない。</p>
 *
 * <p><b>宣言はメソッドも見る（IT4）。</b> 同じ経路でも読みと書きで許すロールが違う。
 * 予約詳細（{@code GET /bookings/{id}}）は営業・経路設計・追跡が読むが、修正
 * （{@code PUT /bookings/{id}}）は営業だけである（US32）。経路だけで宣言すると、
 * 読める人が全員書けることになる。</p>
 */
public final class RoleAuthorization {

    private RoleAuthorization() {
    }

    /** 認証済みなら誰でもよい、を表す。応答の中身は各サービスがロールで絞る。 */
    public static final Set<String> ANY_AUTHENTICATED = Set.of("*");

    private static final String SALES = "ROLE_SALES";
    private static final String ROUTING = "ROLE_ROUTING";
    private static final String TRACKER = "ROLE_TRACKER";
    private static final String ACCOUNTANT = "ROLE_ACCOUNTANT";
    private static final String ADMIN = "ROLE_ADMIN";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** どのメソッドにも当てはまる宣言。 */
    private static final String ANY_METHOD = "*";

    /**
     * 経路の宣言 1 件。メソッドを絞らないときは {@link #ANY_METHOD}。
     */
    private record Rule(String method, String pattern, Set<String> allowed) {

        boolean matches(String requestMethod, String path) {
            return (ANY_METHOD.equals(method) || method.equalsIgnoreCase(requestMethod))
                    && MATCHER.match(pattern, path);
        }
    }

    /**
     * 宣言の並び。**上から順に、最初に当たったものを使う**ので、細かいものを先に置く
     * （{@code /bookings/routing-worklist} は {@code /bookings/**} より前、
     * {@code PUT /bookings/*} は経路だけの宣言より前）。
     */
    private static final List<Rule> RULES = rules();

    private static List<Rule> rules() {
        Map<String, Set<String>> rules = new LinkedHashMap<>();

        // 認証そのもの。ログインは公開経路（PUBLIC_PATHS）なのでここには要らない。
        rules.put("/api/v1/auth/admin/**", Set.of(ADMIN));
        rules.put("/api/v1/auth/**", ANY_AUTHENTICATED);

        // 要確認一覧は宛先ロールでサービス側が絞る。ここで絞ると、ロールが増える
        // たびに 2 か所を直すことになり、片方が置き去りになる。
        rules.put("/api/v1/booking/attention-items/**", ANY_AUTHENTICATED);
        rules.put("/api/v1/booking/attention-items", ANY_AUTHENTICATED);
        rules.put("/api/v1/routing/attention-items/**", ANY_AUTHENTICATED);
        rules.put("/api/v1/routing/attention-items", ANY_AUTHENTICATED);

        // 荷主（S10 / S11）は営業と経理。
        rules.put("/api/v1/booking/shippers/**", Set.of(SALES, ACCOUNTANT));
        rules.put("/api/v1/booking/shippers", Set.of(SALES, ACCOUNTANT));

        // 経路設計作業一覧（S30）と引き渡しは経路設計者だけ。
        // **/bookings/** より先に置く。** 後ろに置くと広いほうに吸われる。
        rules.put("/api/v1/booking/bookings/routing-worklist", Set.of(ROUTING));
        rules.put("/api/v1/booking/bookings/*/routing-request", Set.of(SALES));
        // 経路候補の算出と経路の確定（US08・US09）は経路設計者だけ。
        // **/bookings/** より先に置く。** GET は既存の広い宣言と同じメソッドなので、
        // 順序でしか絞れない（後ろに置くと営業・追跡にも開いたままになる）。
        rules.put("/api/v1/booking/bookings/*/route-candidates", Set.of(ROUTING));
        // 航海を止める前に巻き込む予約を数える（S34 / US24）。読むのは経路設計者
        // だけ。**/bookings/** より先に置く。** 後ろに置くと営業・追跡にも開く。
        rules.put("/api/v1/booking/bookings/by-voyage/*", Set.of(ROUTING));
        // 見直しを頼まれている予約（S02 / 営業。US10 §4）。**/bookings/** より先に
        // 置く。後ろに置くと経路設計・追跡にも開く。
        rules.put("/api/v1/booking/bookings/condition-reviews", Set.of(SALES));
        rules.put("/api/v1/booking/bookings/*/route", Set.of(ROUTING));

        // 予約（S20 / S21）は営業・経路設計・追跡が読む。
        rules.put("/api/v1/booking/bookings/**", Set.of(SALES, ROUTING, TRACKER));
        rules.put("/api/v1/booking/bookings", Set.of(SALES, ROUTING, TRACKER));

        // 航海（S32 / S33）は経路設計者だけ。
        rules.put("/api/v1/routing/voyages/**", Set.of(ROUTING));
        rules.put("/api/v1/routing/voyages", Set.of(ROUTING));

        List<Rule> ordered = new java.util.ArrayList<>();
        // メソッドを絞る宣言を先に置く。後ろに置くと、経路だけの宣言に吸われる
        // （予約の修正（US32）は営業だけだが、参照は経路設計・追跡にも開く）。
        // 条件の調整は経路設計者だけ（US10）。**PUT /bookings/* との順序は問わない。**
        // AntPathMatcher の `*` は `/` をまたがないので、/bookings/*/route-specification
        // は /bookings/* に当たらない（IT6 で実測。計画は「前に積む必要がある」と
        // 書いていたが誤り）。**吸われる先は経路だけの宣言 /bookings/** のほう**で、
        // これは ordered の後ろに積まれるマップ側にある。宣言そのものを外すと営業に
        // 開くことを検査で確かめている。
        ordered.add(new Rule("PUT", "/api/v1/booking/bookings/*/route-specification",
                Set.of(ROUTING)));
        // 差し戻しは経路設計者だけ。POST なのでメソッド込みで宣言する。
        ordered.add(new Rule("POST", "/api/v1/booking/bookings/*/condition-review",
                Set.of(ROUTING)));
        ordered.add(new Rule("PUT", "/api/v1/booking/bookings/*", Set.of(SALES)));
        rules.forEach((pattern, allowed) -> ordered.add(new Rule(ANY_METHOD, pattern, allowed)));
        return List.copyOf(ordered);
    }

    /**
     * その経路に、そのメソッドの宣言があるか。無ければ通さない。
     *
     * <p><b>メソッドを渡す。</b> 決定 6 で認可は（メソッド, 経路）の 2 次元になった。
     * 経路だけで見ると、書き込みの経路を足しても「読み向けの広い宣言」に当たって
     * 「宣言がある」と読めてしまう（載せ忘れた書き込みほど無防備になる）。</p>
     */
    public static boolean isDeclared(String method, String path) {
        return matching(method, path).isPresent();
    }

    /** 宣言されたロールのどれかを持っているか。 */
    public static boolean isAllowed(String method, String path, List<String> roles) {
        return matching(method, path)
                .map(allowed -> allowed.equals(ANY_AUTHENTICATED)
                        || roles.stream().anyMatch(allowed::contains))
                .orElse(false);
    }

    /**
     * 最初に当たった宣言。**空の集合を返さない。** 「宣言が無い」と
     * 「宣言はあるが誰も許さない」は意味が違い、前者は通さない側に倒す。
     *
     * <p>{@link #ANY_METHOD} を渡した場合は「どれかのメソッドに宣言があるか」を見る。
     * 経路の存在を確かめる用途（{@link #isDeclared}）にだけ使う。</p>
     */
    private static Optional<Set<String>> matching(String method, String path) {
        for (Rule rule : RULES) {
            if (ANY_METHOD.equals(method)
                    ? MATCHER.match(rule.pattern(), path)
                    : rule.matches(method, path)) {
                return Optional.of(rule.allowed());
            }
        }
        return Optional.empty();
    }

    /** 宣言している経路パターン（検査が空振りしていないことの確認に使う）。 */
    public static List<String> declaredPatterns() {
        return RULES.stream().map(Rule::pattern).distinct().toList();
    }
}
