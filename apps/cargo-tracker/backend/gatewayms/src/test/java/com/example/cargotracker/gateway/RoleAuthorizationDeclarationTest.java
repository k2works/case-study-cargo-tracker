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
}
