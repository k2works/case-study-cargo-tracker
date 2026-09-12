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
    private static final String SHIPPER = "ROLE_SHIPPER";
    private static final String HANDLER = "ROLE_HANDLER";

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
        // 請求の要確認（IT13）。**自分の担当宛だけ**をサービス側が返すので、
        // ここは認証済みなら通す（booking・routing と同じ形）。
        rules.put("/api/v1/billing/attention-items/**", ANY_AUTHENTICATED);
        rules.put("/api/v1/billing/attention-items", ANY_AUTHENTICATED);

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
        // 確定を待っている予約は営業の受け皿（US13 §受入基準 3）。
        rules.put("/api/v1/booking/bookings/awaiting-confirmation", Set.of(SALES));
        // 追跡番号の発行待ちは経路設計者の受け皿（US13 §受入基準 3 の代わり）。
        rules.put("/api/v1/booking/bookings/awaiting-tracking-number", Set.of(ROUTING));
        rules.put("/api/v1/booking/bookings/*/route", Set.of(ROUTING));

        // 予約（S20 / S21）は営業・経路設計・追跡が読む。
        rules.put("/api/v1/booking/bookings/**", Set.of(SALES, ROUTING, TRACKER));
        rules.put("/api/v1/booking/bookings", Set.of(SALES, ROUTING, TRACKER));

        // 追跡（S40 / S41）は追跡管理者と荷主。**荷主には自社のぶんだけ**を
        // trackingms が X-Auth-Shipper-Id で絞る。ここで荷主を外すと、荷主は
        // 自社の貨物すら追えない（ui_design.md:144-145）。
        // **公開照会（/tracking/public/**）はここに要らない**——PUBLIC_PATHS が
        // 認証そのものを外すので、ロールの宣言は通らない。
        rules.put("/api/v1/tracking/trackings/**", Set.of(TRACKER, SHIPPER));
        rules.put("/api/v1/tracking/trackings", Set.of(TRACKER, SHIPPER));

        // 荷役履歴（S51）は**荷役と追跡の両方**（ui_design.md:236）。
        // 追跡管理者は問い合わせを受けたときに現場の記録を確かめる。
        // **読みだけ。** 書き込みは下の ordered でメソッド込みに宣言する。
        rules.put("/api/v1/handling/*/activities", Set.of(HANDLER, TRACKER));
        // 荷役の記録（S50）は荷役だけ。**/handling/** より先に置く。
        rules.put("/api/v1/handling/voyages/**", Set.of(HANDLER));
        rules.put("/api/v1/handling/cargos/**", Set.of(HANDLER));
        // 通関（S52 / S53）の**読み**は荷役と追跡の両方（ui_design.md:238）。
        // 荷役が申告を出し、追跡が状態を更新する——どちらか一方にすると
        // 片方が自分の仕事の一覧を開けない。
        // **/handling/** より先に置く。** 後ろに置くと荷役だけの宣言に吸われ、
        // 追跡管理者が通関の一覧を開けなくなる。
        // **経理も読む**（IT13 のレビュー 高）。留置の保管料を調整の根拠に指すので、
        // その申告を開けないと根拠を確かめられない。書き込み（登録・状態更新）は
        // 下の ordered がメソッド込みで荷役・追跡に限っている。
        rules.put("/api/v1/handling/customs-declarations/**",
                Set.of(HANDLER, TRACKER, ACCOUNTANT));
        rules.put("/api/v1/handling/customs-declarations", Set.of(HANDLER, TRACKER, ACCOUNTANT));
        rules.put("/api/v1/handling/**", Set.of(HANDLER));

        // 荷主向けの請求書（S62 / US23・IT14）は**荷主だけ**。経理向けの
        // /invoices とは別の経路にする——同じ経路にロールで分岐を足すと、載せ忘れた
        // 分岐ほど無防備になる。**/invoices/** より先に置く**（後ろだと吸われる）。
        // **自社のぶんだけ**を billingms が X-Auth-Shipper-Id で絞る。書き込みの
        // 経路はこのコントローラに存在しない（ShipperInvoiceControllerIsReadOnly が固定）。
        rules.put("/api/v1/billing/shipper-invoices/**", Set.of(SHIPPER));
        rules.put("/api/v1/billing/shipper-invoices", Set.of(SHIPPER));

        // 請求（S60 / S61）は経理だけ（US21 §受入基準 1）。
        rules.put("/api/v1/billing/invoices/**", Set.of(ACCOUNTANT));
        rules.put("/api/v1/billing/invoices", Set.of(ACCOUNTANT));


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
        // **返事は営業だけ。** 経路の `*` は `/` をまたがないので、
        // `/condition-review/response` は上の宣言に当たらない（別に要る）。
        ordered.add(new Rule("POST", "/api/v1/booking/bookings/*/condition-review/response",
                Set.of(SALES)));
        ordered.add(new Rule("POST", "/api/v1/booking/bookings/*/condition-review",
                Set.of(ROUTING)));
        // 荷主への通知と、通知後の経路設計への差し戻しは営業だけ（US12）。
        // **GET /notifications は宣言を足さない。** 広い /bookings/** が
        // {SALES, ROUTING, TRACKER} で、通知履歴に許したいロールと同じである。
        // 同じ集合の宣言を重ねると、片方だけ直したときに食い違う。
        ordered.add(new Rule("POST", "/api/v1/booking/bookings/*/notifications",
                Set.of(SALES)));
        ordered.add(new Rule("POST", "/api/v1/booking/bookings/*/return-to-routing",
                Set.of(SALES)));
        // 予約の確定は営業だけ（US13）。荷主の承認を確認するのは営業の仕事である。
        ordered.add(new Rule("POST", "/api/v1/booking/bookings/*/confirmation",
                Set.of(SALES)));
        // 追跡番号の発行は**経路設計者だけ**（US14 / ui_design.md S22）。
        // 営業に開くと、経路設計者の手番を飛ばして発行できてしまう。
        ordered.add(new Rule("POST", "/api/v1/booking/bookings/*/tracking-number",
                Set.of(ROUTING)));
        // 状態の手動更新は**追跡管理者だけ**（US17）。荷主に開くと、自分の貨物の
        // 状態を書き換えられる。**読みの宣言（TRACKER, SHIPPER）より先に置く**。
        ordered.add(new Rule("POST", "/api/v1/tracking/trackings/*/status", Set.of(TRACKER)));
        // 例外の起票・対応開始・解決・通知の記録は**追跡管理者だけ**（US19）。
        // **読みの宣言（TRACKER, SHIPPER）より先に置く。** 後ろに置くと書き込みが
        // 広いほうに吸われ、荷主が自分の貨物に例外を起票できてしまう。
        // 経路の `*` は `/` をまたがないので、階層ごとに宣言が要る。
        ordered.add(new Rule("POST", "/api/v1/tracking/trackings/*/exceptions/*/response",
                Set.of(TRACKER)));
        ordered.add(new Rule("POST", "/api/v1/tracking/trackings/*/exceptions/*/resolution",
                Set.of(TRACKER)));
        ordered.add(new Rule("POST", "/api/v1/tracking/trackings/*/exceptions/*/notifications",
                Set.of(TRACKER)));
        ordered.add(new Rule("POST", "/api/v1/tracking/trackings/*/exceptions",
                Set.of(TRACKER)));
        // 例外一覧の**読み**は追跡管理者と管理者（US20 §受入基準 3 / IT11）。
        // **書き込みの宣言より後、読みの広い宣言（TRACKER, SHIPPER）より先。**
        // 管理者は緊急の知らせを読む側で、起票も解決もしない——上の POST の
        // 宣言に ADMIN を入れていないのはそのためである。
        // **荷主には開かない。** 他社の貨物の例外まで並んでしまう。
        // **経理も読む**（IT13 のレビュー 高）。請求の調整は誤配・破損の例外を
        // 根拠に指すので、その根拠を開けないと「なぜこの減額か」を確かめられない。
        // **読みだけ。** 起票・対応・解決の POST は上の宣言が追跡管理者に限る。
        ordered.add(new Rule("GET", "/api/v1/tracking/trackings/exceptions",
                Set.of(TRACKER, ADMIN, ACCOUNTANT)));
        // 荷役の記録と取り消しは**荷役作業員だけ**（US15 / IT10 枠 B）。
        // **履歴の宣言（HANDLER, TRACKER）より先に置く。** 後ろに置くと、
        // 同じ経路への書き込みが読み向けの広い宣言に吸われ、
        // 現場の記録を読める追跡管理者が記録も取り消しもできることになる。
        ordered.add(new Rule("POST", "/api/v1/handling/*/activities", Set.of(HANDLER)));
        ordered.add(new Rule("POST", "/api/v1/handling/activities", Set.of(HANDLER)));
        ordered.add(new Rule("POST", "/api/v1/handling/activities/*/void", Set.of(HANDLER)));
        // 通関申告の**登録は荷役作業員だけ**（US29 §受入基準 1。申告は現場が出す）。
        // **状態の更新は追跡管理者だけ**（§受入基準 2。税関とのやりとりを追う側）。
        // **読みの宣言（HANDLER, TRACKER）より先に置く。** 後ろに置くと、
        // 同じ経路への書き込みが読み向けの広い宣言に吸われ、
        // 一覧を読める側が登録も更新もできることになる。
        ordered.add(new Rule("POST", "/api/v1/handling/customs-declarations",
                Set.of(HANDLER)));
        ordered.add(new Rule("POST", "/api/v1/handling/customs-declarations/*/status",
                Set.of(TRACKER)));
        // **予約の参照だけ経理にも開く**（IT13 のレビュー 高）。要確認一覧
        // （S70）は「算出できなかった予約」を経理宛に出すのに、その予約を
        // 開けないと**気づいた先が行き止まり**になる。正典の画面遷移も
        // S70 → S22 を経理の導線として書いている（ui_design.md）。
        //
        // **一覧の経路を先に宣言する。** `/bookings/*` は `/` をまたがないが、
        // `routing-worklist` のような**ロール専用の一覧も同じ形**なので、
        // 先に置かないとこの宣言に吸われて全員に開いてしまう（実測。
        // 経路設計者の作業一覧が営業にも見えた）。
        for (String roleSpecificList : List.of(
                "/api/v1/booking/bookings/routing-worklist",
                "/api/v1/booking/bookings/condition-reviews",
                "/api/v1/booking/bookings/awaiting-confirmation",
                "/api/v1/booking/bookings/awaiting-tracking-number")) {
            ordered.add(new Rule("GET", roleSpecificList, rules.get(roleSpecificList)));
        }
        // **GET だけ。** 書き込みの宣言（PUT / POST）は別に置いてあるので、
        // 経理が予約を書き換えられることはない。
        ordered.add(new Rule("GET", "/api/v1/booking/bookings/*",
                Set.of(SALES, ROUTING, TRACKER, ACCOUNTANT)));
        // 料金の調整は経理だけ（US21 §受入基準 6）。**メソッド込みで宣言する**
        // ——読み向けの広い宣言に吸われると、載せ忘れた書き込みほど無防備になる。
        ordered.add(new Rule("POST", "/api/v1/billing/invoices/*/adjustments",
                Set.of(ACCOUNTANT)));
        // 調整の取り消しも経理だけ（IT14 引き継ぎ C）。
        ordered.add(new Rule("POST", "/api/v1/billing/invoices/*/adjustments/*/reversal",
                Set.of(ACCOUNTANT)));
        // 作れなかった請求を作り直すのも経理だけ（IT14 引き継ぎ B）。
        ordered.add(new Rule("POST", "/api/v1/billing/invoices/recalculate",
                Set.of(ACCOUNTANT)));
        // 発行・入金・取消は経理だけ（US23）。**メソッド込みで宣言する**——
        // 読み向けの広い宣言に吸われると、載せ忘れた書き込みほど無防備になる。
        ordered.add(new Rule("POST", "/api/v1/billing/invoices/*/issue", Set.of(ACCOUNTANT)));
        ordered.add(new Rule("POST", "/api/v1/billing/invoices/*/payments",
                Set.of(ACCOUNTANT)));
        ordered.add(new Rule("POST", "/api/v1/billing/invoices/*/void", Set.of(ACCOUNTANT)));

        // **要確認の確認済は、その要確認の担当ロールがサーバで確かめる**
        // （IT14 引き継ぎ A）。ここで絞ると、ロールが増えるたびに 2 か所を直す
        // ことになる——一覧に出す条件と同じ条件を更新にも置くほうが確かである。
        for (String service : new String[] {"booking", "routing", "billing"}) {
            ordered.add(new Rule("POST",
                    "/api/v1/" + service + "/attention-items/*/acknowledge",
                    ANY_AUTHENTICATED));
        }
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
