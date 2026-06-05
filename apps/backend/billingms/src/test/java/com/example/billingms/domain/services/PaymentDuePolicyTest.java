package com.example.billingms.domain.services;

import com.example.billingms.config.BillingProperties;
import com.example.billingms.domain.model.ShipperType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PaymentDuePolicy} 単体テスト（IT7 T4.2 / review 中対応リファクタ後）。
 *
 * <p>支払期限 = 発行日 + N 日（{@link BillingProperties#paymentDueDays()} で構成）。
 * 月跨ぎ / 閏年での整合性も検証。</p>
 */
class PaymentDuePolicyTest {

    private static final BillingProperties.Overdue OVERDUE =
            new BillingProperties.Overdue("0 0 9 * * *", "Asia/Tokyo");

    private PaymentDuePolicy policy(int days) {
        return new PaymentDuePolicy(
                new BillingProperties(days, Map.of(), OVERDUE, "法人割引（%d%%）", null));
    }

    @Test
    @DisplayName("US23: 発行日 + 30 日が支払期限（デフォルト）")
    void 発行日30日後が支払期限() {
        LocalDate due = policy(30).calculateDueDate(LocalDate.of(2026, 9, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("US23: 月末発行でも 30 日後を計算する（月跨ぎ）")
    void 月末発行で30日後() {
        LocalDate due = policy(30).calculateDueDate(LocalDate.of(2026, 1, 31));
        assertThat(due).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    @DisplayName("US23: 閏年 2 月でも正しく 30 日後を計算")
    void 閏年2月の30日後() {
        LocalDate due = policy(30).calculateDueDate(LocalDate.of(2024, 2, 15));
        assertThat(due).isEqualTo(LocalDate.of(2024, 3, 16));
    }

    @Test
    @DisplayName("US23: issued が null だと NullPointerException")
    void issuedがnullなら例外() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> policy(30).calculateDueDate(null)
        );
    }

    @Test
    @DisplayName("US23 review 中対応: 60 日設定でも動作する（IT8 NET60 対応の前準備）")
    void 設定値60日でも動作() {
        LocalDate due = policy(60).calculateDueDate(LocalDate.of(2026, 9, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("IT8 T1.9: ShipperType 別 Map 設定で NET60 / NET90 を切替可能")
    void T19_shipperType別の支払サイト() {
        BillingProperties props = new BillingProperties(
                30,
                Map.of(ShipperType.CORPORATE, 60, ShipperType.INDIVIDUAL, 90),
                OVERDUE,
                "法人割引（%d%%）",
                null);
        PaymentDuePolicy p = new PaymentDuePolicy(props);
        LocalDate issued = LocalDate.of(2026, 9, 1);

        assertThat(p.calculateDueDate(issued, ShipperType.CORPORATE)).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(p.calculateDueDate(issued, ShipperType.INDIVIDUAL)).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("IT8 T1.9: Map 未設定 ShipperType は default の paymentDueDays に fallback")
    void T19_Map未設定はdefaultにfallback() {
        // CORPORATE のみ Map 設定、INDIVIDUAL は未設定
        BillingProperties props = new BillingProperties(
                30,
                Map.of(ShipperType.CORPORATE, 60),
                OVERDUE,
                "法人割引（%d%%）",
                null);
        PaymentDuePolicy p = new PaymentDuePolicy(props);
        LocalDate issued = LocalDate.of(2026, 9, 1);

        // CORPORATE は Map から 60
        assertThat(p.calculateDueDate(issued, ShipperType.CORPORATE)).isEqualTo(LocalDate.of(2026, 10, 31));
        // INDIVIDUAL は default 30
        assertThat(p.calculateDueDate(issued, ShipperType.INDIVIDUAL)).isEqualTo(LocalDate.of(2026, 10, 1));
        // null は default 30
        assertThat(p.calculateDueDate(issued, null)).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("IT8 T1.9: Map 引数 null でも例外なく空 Map に正規化される（compact constructor）")
    void T19_Map_null時は空Mapに正規化() {
        BillingProperties props = new BillingProperties(30, null, OVERDUE, "法人割引（%d%%）", null);
        PaymentDuePolicy p = new PaymentDuePolicy(props);
        LocalDate due = p.calculateDueDate(LocalDate.of(2026, 9, 1));
        assertThat(due).isEqualTo(LocalDate.of(2026, 10, 1));
    }
}
