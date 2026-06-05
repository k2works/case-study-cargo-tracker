package com.example.billingms.domain.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PaymentDuePolicy} 単体テスト（IT7 T4.2、US23 受入基準 1）。
 *
 * <p>支払期限 = 発行日 + 30 日（仕様: iteration_plan-7.md L111）。月跨ぎ / 閏年での整合性も検証。</p>
 */
class PaymentDuePolicyTest {

    private final PaymentDuePolicy policy = new PaymentDuePolicy();

    @Test
    @DisplayName("US23: 発行日 + 30 日が支払期限")
    void 発行日30日後が支払期限() {
        LocalDate issued = LocalDate.of(2026, 9, 1);

        LocalDate due = policy.calculateDueDate(issued);

        assertThat(due).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("US23: 月末発行でも 30 日後を計算する（月跨ぎ）")
    void 月末発行で30日後() {
        LocalDate issued = LocalDate.of(2026, 1, 31);

        LocalDate due = policy.calculateDueDate(issued);

        assertThat(due).isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    @DisplayName("US23: 閏年 2 月でも正しく 30 日後を計算")
    void 閏年2月の30日後() {
        LocalDate issued = LocalDate.of(2024, 2, 15); // 2024 は閏年

        LocalDate due = policy.calculateDueDate(issued);

        assertThat(due).isEqualTo(LocalDate.of(2024, 3, 16));
    }

    @Test
    @DisplayName("US23: issued が null だと NullPointerException")
    void issuedがnullなら例外() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> policy.calculateDueDate(null)
        );
    }
}
