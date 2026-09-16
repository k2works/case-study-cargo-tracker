package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.valueobjects.CargoKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 前提を作る工程（荷主の登録・貨物種別・確認用の紐付け）。
 *
 * <p><b>正常系の担い手とは別のクラスにする。</b> 1 つのファイルに積むと
 * 読みどころが埋もれ、行の上限（500 行）にも当たる——割る理由は行数だが、
 * 割り方は「何を確かめているか」で決める。</p>
 */
class GatewaySetupStepsTest extends GatewayStepsTestSupport {

    /**
     * どの貨物種別でも予約が通る（US33 §受入基準 1）。
     *
     * <p><b>名簿で緩めず数え上げる。</b> 種別を 1 つずつ書く形だと、次に足した
     * 種別の付帯情報が漏れて<b>そのシナリオだけが 422 で止まる</b>——実際に
     * 危険物と冷凍・冷蔵で起きた（実環境で「危険物には危険物申告が必要です」）。</p>
     *
     * <p><b>両方向を見る。</b> 業務は「要るものが無い」だけでなく
     * 「要らないものが付いている」も断る。</p>
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(CargoKind.class)
    @DisplayName("US33 §1: どの貨物種別でも、その種別に要る付帯情報だけを送る")
    void sendsExactlyTheDeclarationEachCargoKindNeeds(CargoKind kind) throws IOException {
        start(Scenario.STANDARD);
        responses.put("/api/v1/booking/bookings", "{\"bookingId\":\"BK-1\"}");

        start(new ScenarioInput(Scenario.STANDARD, "JPTYO", "USNYC", kind.name(),
                BigDecimal.valueOf(1000), 120))
                .execute(StepKind.REGISTER_BOOKING,
                        Map.of(StepKind.REGISTER_SHIPPER, "SHP-1"));

        String body = bodies.stream()
                .filter(it -> it.startsWith("POST /api/v1/booking/bookings "))
                .reduce((first, last) -> last).orElseThrow();
        assertThat(body).contains("\"cargoType\":\"" + kind.name() + "\"");
        // その種別に要るものは入っている。
        for (String field : kind.declaration().keySet()) {
            assertThat(body)
                    .as("%s に要る %s が入っていない（業務が 422 で断る）", kind, field)
                    .contains("\"" + field + "\"");
        }
        // **他の種別のものは入っていない。**「危険物以外に危険物申告は
        // 付けられません」で断られる。
        for (CargoKind other : CargoKind.values()) {
            if (other == kind) {
                continue;
            }
            for (String field : other.declaration().keySet()) {
                if (kind.declaration().containsKey(field)) {
                    continue;
                }
                assertThat(body)
                        .as("%s に %s（%s のもの）が付いている", kind, field, other)
                        .doesNotContain("\"" + field + "\"");
            }
        }
    }

    @Test
    @DisplayName("US37 §6: 確認用の利用者を、いま作った荷主へ紐付ける")
    void linksTheConfirmationUserToTheShipperItJustCreated() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/shippers", "{\"shipperId\":\"SHP-1\"}");

        api.execute(StepKind.REGISTER_SHIPPER, Map.of());

        // **紐付けが無いと、作った貨物の知らせを誰も画面で確かめられない。**
        assertThat(bodies)
                .anyMatch(body -> body.startsWith("POST /api/v1/auth/admin/users/sim01/shipper ")
                        && body.contains("\"shipperId\":\"SHP-1\""));
        // **管理者で叩く。** 紐付けは管理者だけができる操作である。
        assertThat(requests).anyMatch(r ->
                r.startsWith("POST /api/v1/auth/admin/users/sim01/shipper"));
        // **紐付けた直後に 1 度読む。** 知らせは「初めて開いた荷主には何も
        // 出さない」ので、出来事が起きる前に既読の位置を 0 で作っておかないと、
        // この実行の知らせが 1 件も出ない（実クラスタで実測）。
        assertThat(requests).anyMatch(r -> r.startsWith("GET /api/v1/tracking/notices"));
    }

    @Test
    @DisplayName("紐付けに失敗したら、既読の位置は作りに行かない（前の荷主の位置を動かさない）")
    void doesNotPrimeTheReadPositionWhenTheLinkFailed() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/shippers", "{\"shipperId\":\"SHP-1\"}");
        statuses.put("/api/v1/auth/admin/users/sim01/shipper", 500);

        api.execute(StepKind.REGISTER_SHIPPER, Map.of());

        // 紐付いていないのに読むと、**前の実行の荷主**の既読位置が進み、
        // その実行の知らせが消える。
        assertThat(requests).noneMatch(r -> r.startsWith("GET /api/v1/tracking/notices"));
    }

    @Test
    @DisplayName("紐付けに失敗しても実行は止めない（業務の連鎖を読めなくしない）")
    void keepsGoingWhenTheConfirmationLinkFails() throws IOException {
        GatewayBusinessApi api = start(Scenario.STANDARD);
        responses.put("/api/v1/booking/shippers", "{\"shipperId\":\"SHP-1\"}");
        statuses.put("/api/v1/auth/admin/users/sim01/shipper", 500);

        var result = api.execute(StepKind.REGISTER_SHIPPER, Map.of());

        // **これは業務の工程ではない。** 段取りの都合でシナリオを失敗させると、
        // 確かめたい業務の連鎖が読めなくなる。
        assertThat(result.succeeded()).isTrue();
        assertThat(result.producedId()).isEqualTo("SHP-1");
    }
}
