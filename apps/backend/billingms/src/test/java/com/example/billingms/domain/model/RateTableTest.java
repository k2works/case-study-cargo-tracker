package com.example.billingms.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RateTable} の不変条件と料金単価取得（US21 / IT7 タスク 2.2）。
 *
 * <p>貨物種別ごとの単価（円/kg/km）と取扱費（円/回）を保持する値オブジェクト。
 * S20 UI のサンプル値（5300km × 1200kg × 0.05 = 318,000 円、8 回 × 1500 円 = 12,000 円）と
 * 整合する係数を default として採用する。</p>
 */
class RateTableTest {

    @Test
    @DisplayName("default テーブルは S20 UI サンプル値と整合する係数を持つ")
    void defaultテーブル() {
        RateTable table = RateTable.defaultTable();

        // GENERAL: 5300 × 1200 × 0.05 = 318,000 円（S20 UI）
        assertThat(table.cargoTypeFactor("GENERAL")).isEqualByComparingTo("0.05");
        // HAZARDOUS / REFRIGERATED は割増係数
        assertThat(table.cargoTypeFactor("HAZARDOUS")).isEqualByComparingTo("0.08");
        assertThat(table.cargoTypeFactor("REFRIGERATED")).isEqualByComparingTo("0.10");
        // 取扱費: 1500 円/回（S20 UI）
        assertThat(table.handlingUnitFee()).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("未知の貨物種別を指定すると IllegalArgumentException")
    void 未知の貨物種別() {
        RateTable table = RateTable.defaultTable();
        assertThatThrownBy(() -> table.cargoTypeFactor("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("rates が null / empty の場合は IllegalArgumentException")
    void ratesがnullまたは空() {
        assertThatThrownBy(() -> new RateTable(null, new BigDecimal("1500")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rates");
        assertThatThrownBy(() -> new RateTable(Map.of(), new BigDecimal("1500")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rates");
    }

    @Test
    @DisplayName("handlingUnitFee が負数の場合は IllegalArgumentException")
    void 取扱費が負数() {
        assertThatThrownBy(() -> new RateTable(
                Map.of("GENERAL", new BigDecimal("0.05")), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("handlingUnitFee");
    }

    @Test
    @DisplayName("カスタムテーブルを作成できる（IT8 で運用設定への移行を想定）")
    void カスタムテーブル() {
        RateTable table = new RateTable(
                Map.of("GENERAL", new BigDecimal("0.06"), "EXPRESS", new BigDecimal("0.12")),
                new BigDecimal("2000")
        );
        assertThat(table.cargoTypeFactor("EXPRESS")).isEqualByComparingTo("0.12");
        assertThat(table.handlingUnitFee()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("rates Map は不変（外部からの変更を拒否）")
    void ratesは不変() {
        Map<String, BigDecimal> mutable = new java.util.HashMap<>();
        mutable.put("GENERAL", new BigDecimal("0.05"));
        RateTable table = new RateTable(mutable, new BigDecimal("1500"));

        mutable.put("GENERAL", new BigDecimal("999"));
        assertThat(table.cargoTypeFactor("GENERAL")).isEqualByComparingTo("0.05");
    }
}
