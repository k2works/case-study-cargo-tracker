package com.example.cargotracker.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.gateway.infrastructure.config.RoleAuthorization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 認可の<b>宣言そのもの</b>を検査する（フィルタの挙動は
 * {@code JwtAuthenticationFilterTest} が見る）。
 *
 * <p><b>名簿方式の検査は「載っていないもの」を通す。</b> 経路を 1 本ずつ検査して
 * いると、次に足す一覧が名簿から漏れたときに誰も気づかない——US30 の承認待ち
 * 一覧が実際に漏れ、営業・経路設計・経理にも開いていた（IT15 のレビュー 高）。
 * ここでは<b>宣言から数え上げて</b>確かめる。</p>
 */
class RoleAuthorizationDeclarationTest {

    @Test
    @DisplayName("ロール専用の一覧は 1 本残らず `/bookings/*` より前に出る")
    void everyRoleSpecificListIsNarrowerThanTheBroadRule() throws Exception {
        // **名簿方式の検査は「載っていないもの」を通す。** 個別の経路を 1 本ずつ
        // 検査していると、次に足す一覧が名簿から漏れたときに誰も気づかない
        // （US30 の承認待ち一覧が実際に漏れた）。**宣言そのものから数え上げる**。
        //
        // `/api/v1/booking/bookings/<1 セグメント>` の形は `GET /bookings/*` と
        // 同じ形をしている。その中でロールを絞っている宣言は、広いほうより
        // 前に出ていなければ意味がない——ここでは実際に叩いて確かめる。
        var narrower = RoleAuthorization.declaredPatterns().stream()
                .filter(pattern -> pattern.matches(
                        "/api/v1/booking/bookings/[a-z0-9-]+"))
                .toList();
        assertThat(narrower).as("対象が 0 件なら検査は空振りしている").isNotEmpty();

        for (String pattern : narrower) {
            var allowed = java.util.List.of(
                    "ROLE_SALES", "ROLE_ROUTING", "ROLE_TRACKER", "ROLE_ACCOUNTANT").stream()
                    .filter(role -> RoleAuthorization.isAllowed("GET", pattern,
                            java.util.List.of(role)))
                    .toList();
            // 広い宣言に吸われていれば 4 ロール全部が通る。絞っているなら通らない
            // ロールがある。**4 つ全部通る宣言は、絞ったつもりで絞れていない**。
            assertThat(allowed)
                    .as(pattern + " が `GET /bookings/*` に吸われている"
                            + "（ロール専用の一覧なら、広い宣言より前に置く）")
                    .hasSizeLessThan(4);
        }
    }

    @Test
    @DisplayName("US34 §5: 管理者は実行結果が指す業務画面を読める（行き止まりにしない）")
    void administratorCanReadWhatTheSimulationProduced() {
        // **「作られたものから業務画面へ行ける」は、行けることを確かめないと守れない。**
        // 実行結果（S93）は予約番号・追跡番号・請求番号からリンクを出すのに、
        // 管理者がその先を開けず 403 になっていた（IT16 のクラスタ E2E で実測）。
        var destinations = java.util.List.of(
                "/api/v1/booking/bookings/55555555-5555-5555-5555-555555555555",
                "/api/v1/tracking/trackings/TRK-0000000001",
                "/api/v1/billing/invoices/INV-0000000001");

        for (String path : destinations) {
            assertThat(RoleAuthorization.isAllowed("GET", path,
                    java.util.List.of("ROLE_ADMIN")))
                    .as(path + " を管理者が読めない（実行結果のリンクが行き止まりになる）")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("N3: 管理者に開くのは単票だけ（業務の一覧までは開けない）")
    void administratorCannotReadBusinessWorklists() {
        // **行き止まりを塞ぐのに要る幅は単票までである。** 一覧まで開くと、
        // 管理者は全社の追跡・請求・予約を並べて読めることになる——US34 §5 の
        // 約束はそこまで求めていない（IT16 のレビュー N3）。
        // **3 つとも揃える。** 予約だけが単票に絞れていて、追跡と請求は
        // 一覧まで開いていた——1 本ずつ直すと、次に足す一覧が漏れる。
        var worklists = java.util.List.of(
                "/api/v1/booking/bookings",
                "/api/v1/tracking/trackings",
                "/api/v1/billing/invoices");

        for (String path : worklists) {
            assertThat(RoleAuthorization.isAllowed("GET", path,
                    java.util.List.of("ROLE_ADMIN")))
                    .as(path + " の一覧が管理者に開いている")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("管理者に開けるのは読みだけ（業務の操作までは開けない）")
    void administratorCannotWriteBusinessData() {
        // **読みを開いたついでに書きまで開かない。** 実行結果から辿れれば足りる。
        var writes = java.util.List.of(
                java.util.Map.entry("POST",
                        "/api/v1/booking/bookings/55555555-5555-5555-5555-555555555555"
                                + "/confirmation"),
                java.util.Map.entry("POST",
                        "/api/v1/tracking/trackings/TRK-0000000001/status"),
                java.util.Map.entry("POST",
                        "/api/v1/billing/invoices/INV-0000000001/issue"),
                java.util.Map.entry("POST",
                        "/api/v1/billing/invoices/INV-0000000001/payments"));

        for (var write : writes) {
            assertThat(RoleAuthorization.isAllowed(write.getKey(), write.getValue(),
                    java.util.List.of("ROLE_ADMIN")))
                    .as(write.getKey() + " " + write.getValue() + " が管理者に開いている")
                    .isFalse();
        }
    }
}
