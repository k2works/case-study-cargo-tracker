package com.example.billingms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TransportRecord} の不変条件検証（US21 / IT7 タスク 2.1）。
 *
 * <p>輸送実績（経路距離・重量・貨物種別・荷役回数）を保持する値オブジェクト。
 * FareCalculator（Task 2.2）への入力として使う。距離・重量は非負、荷役回数は非負整数。
 * cargoType は必須文字列、currency も非空必須。</p>
 */
class TransportRecordTest {

    @Test
    @DisplayName("正常な輸送実績を作成できる")
    void 正常な輸送実績を作成できる() {
        TransportRecord record = new TransportRecord(
                new BigDecimal("5300"),
                new BigDecimal("1200"),
                "GENERAL",
                8,
                "JPY"
        );

        assertThat(record.distanceKm()).isEqualByComparingTo("5300");
        assertThat(record.weightKg()).isEqualByComparingTo("1200");
        assertThat(record.cargoType()).isEqualTo("GENERAL");
        assertThat(record.handlingCount()).isEqualTo(8);
        assertThat(record.currency()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("距離が負数の場合は IllegalArgumentException")
    void 距離が負数() {
        assertThatThrownBy(() -> new TransportRecord(
                new BigDecimal("-1"), new BigDecimal("1200"), "GENERAL", 8, "JPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distanceKm");
    }

    @Test
    @DisplayName("距離が null の場合は IllegalArgumentException")
    void 距離がnull() {
        assertThatThrownBy(() -> new TransportRecord(
                null, new BigDecimal("1200"), "GENERAL", 8, "JPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distanceKm");
    }

    @Test
    @DisplayName("重量が 0 以下の場合は IllegalArgumentException")
    void 重量がゼロ() {
        assertThatThrownBy(() -> new TransportRecord(
                new BigDecimal("5300"), BigDecimal.ZERO, "GENERAL", 8, "JPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weightKg");
    }

    @Test
    @DisplayName("荷役回数が負数の場合は IllegalArgumentException")
    void 荷役回数が負数() {
        assertThatThrownBy(() -> new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "GENERAL", -1, "JPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("handlingCount");
    }

    @Test
    @DisplayName("貨物種別が空の場合は IllegalArgumentException")
    void 貨物種別が空() {
        assertThatThrownBy(() -> new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "  ", 8, "JPY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cargoType");
    }

    @Test
    @DisplayName("通貨が空の場合は IllegalArgumentException")
    void 通貨が空() {
        assertThatThrownBy(() -> new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "GENERAL", 8, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @Test
    @DisplayName("荷役回数 0 回は受理可（FareCalculator で扱う）")
    void 荷役回数ゼロは受理可() {
        TransportRecord record = new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "GENERAL", 0, "JPY");
        assertThat(record.handlingCount()).isZero();
    }
}
