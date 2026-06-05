package com.example.billingms.domain.services;

import com.example.billingms.config.BillingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
        return new PaymentDuePolicy(new BillingProperties(days, OVERDUE, "法人割引（%d%%）"));
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
}
