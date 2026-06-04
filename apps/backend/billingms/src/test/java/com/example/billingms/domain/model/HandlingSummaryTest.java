package com.example.billingms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HandlingSummary} の不変条件検証（domain-model.md L935-939）。
 *
 * <p>荷役実績サマリ。HandlingActivityAcl（Task 2.4）が handlingms から集計して返す。
 * 受領時刻・引取時刻はオプション（未受領 / 未引取の場合 null）、例外調整額は非負必須。</p>
 */
class HandlingSummaryTest {

    @Test
    @DisplayName("受領・引取時刻と例外調整額を保持できる")
    void 全フィールドを保持() {
        LocalDateTime receiveAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        LocalDateTime claimAt = LocalDateTime.of(2026, 8, 16, 14, 0);
        HandlingSummary summary = new HandlingSummary(receiveAt, claimAt, new BigDecimal("5000"));

        assertThat(summary.receiveAt()).isEqualTo(receiveAt);
        assertThat(summary.claimAt()).isEqualTo(claimAt);
        assertThat(summary.exceptionAdjustment()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("受領前は receiveAt null 許容")
    void 受領前はnull() {
        HandlingSummary summary = new HandlingSummary(null, null, BigDecimal.ZERO);
        assertThat(summary.receiveAt()).isNull();
        assertThat(summary.claimAt()).isNull();
        assertThat(summary.exceptionAdjustment()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("例外調整額が null の場合は IllegalArgumentException")
    void 例外調整額がnull() {
        assertThatThrownBy(() -> new HandlingSummary(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceptionAdjustment");
    }

    @Test
    @DisplayName("例外調整額が負数の場合は IllegalArgumentException")
    void 例外調整額が負数() {
        assertThatThrownBy(() -> new HandlingSummary(null, null, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceptionAdjustment");
    }

    @Test
    @DisplayName("claimAt が receiveAt より前の場合は IllegalArgumentException")
    void claimAtがreceiveAtより前() {
        LocalDateTime receiveAt = LocalDateTime.of(2026, 8, 16, 14, 0);
        LocalDateTime claimAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        assertThatThrownBy(() -> new HandlingSummary(receiveAt, claimAt, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimAt");
    }
}
