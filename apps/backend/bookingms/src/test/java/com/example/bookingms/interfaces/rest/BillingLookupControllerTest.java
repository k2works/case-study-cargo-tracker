package com.example.bookingms.interfaces.rest;

import static com.example.bookingms.BillableCargoFixtures.oceanLegs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.example.bookingms.application.port.BillableCargo;
import com.example.bookingms.application.port.BillableCargoFinder;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.BillingSnapshotContract;
import com.example.shared.contract.CargoSnapshotContract;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 料金算出の入力を返す入口（US21・[ADR-027] 決定 7）。
 *
 * <p>呼ぶのは billingms であり、人ではない。{@link CargoLookupController}（handlingms 向け）
 * と<strong>同じ形</strong>にする——終盤で新しい結合方式を発明しない。
 *
 * <p><strong>誤配の記録を載せる</strong>（IT10 レビューの懸念）。IT10 までは予約詳細に
 * しか出ておらず、経理担当者はその画面を開けなかった——「残っている」と「読める」は別である。
 */
@WebMvcTest(BillingLookupController.class)
@DisplayName("料金算出の入力 API（サービス間）")
class BillingLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BillableCargoFinder billable;

    private static String pathFor(String bookingId) {
        return BillingSnapshotContract.PATH.replace("{bookingId}", bookingId);
    }

    private static BillableCargo delivered() {
        return new BillableCargo("BKG-2026000007", "DELIVERED", "1", "丸紅商事株式会社",
                "CORPORATE", new java.math.BigDecimal("0.1000"), new java.math.BigDecimal("4200"),
                "GENERAL", "Tokyo", "JP", "Los Angeles", "US", 2, oceanLegs(2),
                Instant.parse("2027-09-26T00:00:00Z"), null, null);
    }

    @Test
    @DisplayName("既知のサービスは、料金算出に要る項目を受け取れる")
    void returnsSnapshotToTrustedService() throws Exception {
        when(billable.findBillable("BKG-2026000007")).thenReturn(Optional.of(delivered()));

        mockMvc.perform(get(pathFor("BKG-2026000007"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                BillingSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value("BKG-2026000007"))
                .andExpect(jsonPath("$.bookingStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.shipperType").value("CORPORATE"))
                // **率をそのまま運ぶ**（百分率ではない）。shipper.discount_rate 列が率で持っている
                .andExpect(jsonPath("$.discountRate").value(0.1000))
                .andExpect(jsonPath("$.weightKg").value(4200))
                .andExpect(jsonPath("$.cargoType").value("GENERAL"))
                // **区間数を返す。** 距離は持っていないため、これが料金の入力になる
                .andExpect(jsonPath("$.legCount").value(2));
    }

    /**
     * <strong>誤配の記録を運ぶ</strong>（US28-8・IT10 レビューの懸念）。
     *
     * <p>載せないと、経理担当者は料金調整の根拠を読む手段を持たない。
     */
    @Test
    @DisplayName("誤配した貨物では、いつ・どこで外れたかを運ぶ")
    void carriesTheMisrouteRecord() throws Exception {
        when(billable.findBillable("BKG-2026000009")).thenReturn(Optional.of(
                new BillableCargo("BKG-2026000009", "DELIVERED", "1", "丸紅商事株式会社",
                        "CORPORATE", new java.math.BigDecimal("0.1000"),
                        new java.math.BigDecimal("2500"), "GENERAL", "Tokyo", "JP", "Los Angeles", "US", 1, oceanLegs(1),
                        Instant.parse("2027-10-02T00:00:00Z"),
                        new BillableCargo.Misroute(Instant.parse("2027-09-09T00:00:00Z"),
                                "SGSIN", "Singapore"),
                        null)));

        mockMvc.perform(get(pathFor("BKG-2026000009"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                BillingSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.misroute.at").exists())
                .andExpect(jsonPath("$.misroute.locationUnLocode").value("SGSIN"))
                .andExpect(jsonPath("$.misroute.locationName").value("Singapore"));
    }

    /** 誤配していなければ、項目ごと現れない。**毎回 null が出ると、あるかないかを判定しにくい。** */
    @Test
    @DisplayName("誤配していない貨物では、誤配の項目が現れない")
    void omitsTheMisrouteWhenThereIsNone() throws Exception {
        when(billable.findBillable("BKG-2026000007")).thenReturn(Optional.of(delivered()));

        mockMvc.perform(get(pathFor("BKG-2026000007"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                BillingSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.misroute").doesNotExist());
    }

    /**
     * <strong>キャンセルは「申請した時点の状態」を運ぶ</strong>（US30-9・[ADR-027] 注 16-a）。
     *
     * <p>料率は申請時の状態で決まる——承認された時点ではない。輸送中に申請したものは、
     * 承認が翌日でも輸送中の料率になる。
     */
    @Test
    @DisplayName("キャンセルされた貨物では、申請した時点の状態を運ぶ")
    void carriesTheStatusAtRequestNotAtApproval() throws Exception {
        when(billable.findBillable("BKG-2026000010")).thenReturn(Optional.of(
                new BillableCargo("BKG-2026000010", "CANCELLED", "1", "丸紅商事株式会社",
                        "CORPORATE", new java.math.BigDecimal("0.1000"),
                        new java.math.BigDecimal("1500"), "GENERAL", "Tokyo", "JP", "Los Angeles", "US", 1, oceanLegs(1),
                        null, null,
                        new BillableCargo.Cancellation("IN_TRANSIT",
                                Instant.parse("2027-09-10T00:00:00Z")))));

        mockMvc.perform(get(pathFor("BKG-2026000010"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                BillingSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellation.bookingStatusAtRequest").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.cancellation.requestedAt").exists());
    }

    /**
     * <strong>名簿に無い主体は通さない</strong>（[ADR-015] 以来の許可リスト方式）。
     *
     * <p>handlingms は荷役の照会には通るが、<strong>料金の入力は読めない</strong>——
     * 荷主の社名も割引率も、荷役作業員には要らない。
     */
    @ParameterizedTest
    @ValueSource(strings = {"system:handlingms", "system:trackingms", "sales01", "accountant01"})
    @DisplayName("名簿に無い主体は通さない")
    void rejectsCallersOutsideTheRoster(String caller) throws Exception {
        mockMvc.perform(get(pathFor("BKG-2026000007"))
                        .header(AuthenticatedUser.USER_ID_HEADER, caller))
                .andExpect(status().isForbidden());
    }

    /** 荷役の照会の主体をそのまま流用していないことを、契約の値どうしで確かめる。 */
    @Test
    @DisplayName("荷役の照会とは別の主体である")
    void usesADistinctPrincipalFromTheHandlingLookup() {
        org.assertj.core.api.Assertions
                .assertThat(BillingSnapshotContract.CALLER_PRINCIPAL)
                .isNotEqualTo(CargoSnapshotContract.CALLER_PRINCIPAL);
    }

    @Test
    @DisplayName("料金算出の対象でない予約は 404 を返す")
    void returnsNotFoundForCargoThatCannotBeBilled() throws Exception {
        when(billable.findBillable("BKG-2026000001")).thenReturn(Optional.empty());

        mockMvc.perform(get(pathFor("BKG-2026000001"))
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                BillingSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>料金を算出していない予約を並べる。</strong>
     *
     * <p>経理担当者は他に気づく手段を持たない（メールの仕組みは無い）。
     */
    @Test
    @DisplayName("料金算出の対象になる予約を並べる")
    void listsBillableCargoes() throws Exception {
        when(billable.findAllBillable()).thenReturn(List.of(delivered()));

        mockMvc.perform(get(BillingSnapshotContract.UNBILLED_PATH)
                        .header(AuthenticatedUser.USER_ID_HEADER,
                                BillingSnapshotContract.CALLER_PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value("BKG-2026000007"))
                .andExpect(jsonPath("$[0].shipperName").value("丸紅商事株式会社"));
    }
}
