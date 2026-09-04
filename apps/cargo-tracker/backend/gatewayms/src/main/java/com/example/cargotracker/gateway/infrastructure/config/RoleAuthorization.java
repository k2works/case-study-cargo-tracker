package com.example.cargotracker.gateway.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 */
public final class RoleAuthorization {

    private RoleAuthorization() {
    }

    /** 認証済みなら誰でもよい、を表す。応答の中身は各サービスがロールで絞る。 */
    public static final Set<String> ANY_AUTHENTICATED = Set.of("*");

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * 経路パターン → 許すロール。**上から順に、最初に当たったものを使う**ので、
     * 細かい経路を先に置く（{@code /bookings/routing-worklist} は
     * {@code /bookings/**} より前）。
     */
    private static final Map<String, Set<String>> RULES = rules();

    private static Map<String, Set<String>> rules() {
        Map<String, Set<String>> rules = new LinkedHashMap<>();

        // 認証そのもの。ログインは公開経路（PUBLIC_PATHS）なのでここには要らない。
        rules.put("/api/v1/auth/admin/**", Set.of("ROLE_ADMIN"));
        rules.put("/api/v1/auth/**", ANY_AUTHENTICATED);

        // 要確認一覧は宛先ロールでサービス側が絞る。ここで絞ると、ロールが増える
        // たびに 2 か所を直すことになり、片方が置き去りになる。
        rules.put("/api/v1/booking/attention-items/**", ANY_AUTHENTICATED);
        rules.put("/api/v1/booking/attention-items", ANY_AUTHENTICATED);
        rules.put("/api/v1/routing/attention-items/**", ANY_AUTHENTICATED);
        rules.put("/api/v1/routing/attention-items", ANY_AUTHENTICATED);

        // 荷主（S10 / S11）は営業と経理。
        rules.put("/api/v1/booking/shippers/**", Set.of("ROLE_SALES", "ROLE_ACCOUNTANT"));
        rules.put("/api/v1/booking/shippers", Set.of("ROLE_SALES", "ROLE_ACCOUNTANT"));

        // 経路設計作業一覧（S30）と引き渡しは経路設計者だけ。
        // **/bookings/** より先に置く。** 後ろに置くと広いほうに吸われる。
        rules.put("/api/v1/booking/bookings/routing-worklist", Set.of("ROLE_ROUTING"));
        rules.put("/api/v1/booking/bookings/*/routing-request", Set.of("ROLE_SALES"));

        // 予約（S20 / S21）は営業・経路設計・追跡。
        rules.put("/api/v1/booking/bookings/**", Set.of("ROLE_SALES", "ROLE_ROUTING",
                "ROLE_TRACKER"));
        rules.put("/api/v1/booking/bookings", Set.of("ROLE_SALES", "ROLE_ROUTING",
                "ROLE_TRACKER"));

        // 航海（S32 / S33）は経路設計者だけ。
        rules.put("/api/v1/routing/voyages/**", Set.of("ROLE_ROUTING"));
        rules.put("/api/v1/routing/voyages", Set.of("ROLE_ROUTING"));

        // Map.copyOf にしない。順序が失われると「細かい経路を先に」が壊れる。
        return java.util.Collections.unmodifiableMap(rules);
    }

    /** その経路に宣言があるか。無ければ通さない。 */
    public static boolean isDeclared(String path) {
        return matching(path) != null;
    }

    /** 宣言されたロールのどれかを持っているか。 */
    public static boolean isAllowed(String path, List<String> roles) {
        Set<String> allowed = matching(path);
        if (allowed == null) {
            return false;
        }
        if (allowed.equals(ANY_AUTHENTICATED)) {
            return true;
        }
        return roles.stream().anyMatch(allowed::contains);
    }

    private static Set<String> matching(String path) {
        for (Map.Entry<String, Set<String>> entry : RULES.entrySet()) {
            if (MATCHER.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 宣言している経路パターン（検査が空振りしていないことの確認に使う）。 */
    public static List<String> declaredPatterns() {
        return List.copyOf(RULES.keySet());
    }
}
