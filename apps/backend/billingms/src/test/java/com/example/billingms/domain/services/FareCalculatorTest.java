package com.example.billingms.domain.services;

import com.example.billingms.domain.model.RateTable;
import com.example.billingms.domain.model.TransportRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FareCalculator} の基本料金計算（US21 / IT7 タスク 2.2）。
 *
 * <p>計算式: {@code basicFee = weight × distance × cargoTypeFactor + handlingCount × handlingUnitFee}
 * S20 UI のサンプル値（5300km × 1200kg × 0.05 + 8 回 × 1500 円 = 318,000 + 12,000 = 330,000 円）と
 * 一致することを検証する。</p>
 */
class FareCalculatorTest {

    private final FareCalculator calculator = new FareCalculator(RateTable.defaultTable());

    @Test
    @DisplayName("S20 UI サンプル: 5300km × 1200kg × 0.05 + 8 回 × 1500 = 330,000 円")
    void S20サンプルと一致() {
        TransportRecord record = new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "GENERAL", 8, "JPY");

        BigDecimal fare = calculator.calculate(record);

        assertThat(fare).isEqualByComparingTo("330000");
    }

    @Test
    @DisplayName("HAZARDOUS は GENERAL の 1.6 倍係数（0.08 / 0.05）")
    void HAZARDOUSの割増係数() {
        TransportRecord record = new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "HAZARDOUS", 8, "JPY");

        BigDecimal fare = calculator.calculate(record);

        // 5300 × 1200 × 0.08 = 508,800 円、+ 12,000 = 520,800 円
        assertThat(fare).isEqualByComparingTo("520800");
    }

    @Test
    @DisplayName("REFRIGERATED は GENERAL の 2.0 倍係数（0.10 / 0.05）")
    void REFRIGERATEDの割増係数() {
        TransportRecord record = new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "REFRIGERATED", 8, "JPY");

        BigDecimal fare = calculator.calculate(record);

        // 5300 × 1200 × 0.10 = 636,000 円、+ 12,000 = 648,000 円
        assertThat(fare).isEqualByComparingTo("648000");
    }

    @Test
    @DisplayName("荷役回数 0 回でも基本料金は算出される（取扱費 = 0）")
    void 荷役回数ゼロ() {
        TransportRecord record = new TransportRecord(
                new BigDecimal("5300"), new BigDecimal("1200"), "GENERAL", 0, "JPY");

        BigDecimal fare = calculator.calculate(record);

        // 318,000 円のみ（取扱費なし）
        assertThat(fare).isEqualByComparingTo("318000");
    }

    @Test
    @DisplayName("距離 0 でも荷役費だけは加算される（在港料金）")
    void 距離ゼロ() {
        TransportRecord record = new TransportRecord(
                BigDecimal.ZERO, new BigDecimal("1200"), "GENERAL", 8, "JPY");

        BigDecimal fare = calculator.calculate(record);

        // 0 + 12,000 = 12,000 円
        assertThat(fare).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("結果は小数第 0 位（HALF_UP 丸め、円単位）")
    void 円単位の丸め() {
        // 1234 × 1234 = 1,522,756 → × 0.05 = 76,137.80 → 76,138 円（HALF_UP）
        TransportRecord record = new TransportRecord(
                new BigDecimal("1234"), new BigDecimal("1234"), "GENERAL", 0, "JPY");

        BigDecimal fare = calculator.calculate(record);

        assertThat(fare).isEqualByComparingTo("76138");
    }

    @Test
    @DisplayName("カスタム RateTable で計算できる")
    void カスタムRateTable() {
        RateTable customTable = new RateTable(
                Map.of("EXPRESS", new BigDecimal("0.20")), new BigDecimal("3000"));
        FareCalculator custom = new FareCalculator(customTable);

        TransportRecord record = new TransportRecord(
                new BigDecimal("1000"), new BigDecimal("500"), "EXPRESS", 2, "JPY");

        // 1000 × 500 × 0.20 = 100,000 + 2 × 3000 = 6,000 = 106,000
        assertThat(custom.calculate(record)).isEqualByComparingTo("106000");
    }

    @Test
    @DisplayName("rateTable が null の場合は NullPointerException でコンストラクタが失敗する")
    void rateTableがnull() {
        assertThatThrownBy(() -> new FareCalculator(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rateTable");
    }

    @Test
    @DisplayName("transport が null の場合は IllegalArgumentException")
    void transportがnull() {
        assertThatThrownBy(() -> calculator.calculate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transport");
    }
}
