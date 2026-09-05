package com.example.cargotracker.booking.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.service.CargoSpecificationDiff.CargoSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 修正前後の差分（US32 §受入基準 4）。 */
class CargoSpecificationDiffTest {

    private static CargoSnapshot snapshot() {
        return new CargoSnapshot("JPTYO", "USNYC", LocalDate.of(2026, 12, 1), "GENERAL",
                new BigDecimal("1200.00"), null, null, null, 3, "自動車部品",
                null, null, null, null);
    }

    @Test
    @DisplayName("変わっていなければ差分は空")
    void noChanges() {
        assertThat(CargoSpecificationDiff.between(snapshot(), snapshot())).isEmpty();
    }

    @Test
    @DisplayName("変わった項目だけが、利用者の言葉で並ぶ")
    void reportsChangedFields() {
        CargoSnapshot after = new CargoSnapshot("JPTYO", "NLRTM",
                LocalDate.of(2026, 12, 1), "GENERAL",
                new BigDecimal("1500.00"), null, null, null, 3, "自動車部品",
                null, null, null, null);

        assertThat(CargoSpecificationDiff.between(snapshot(), after))
                .extracting(CargoSpecificationDiff.FieldChange::label,
                        CargoSpecificationDiff.FieldChange::before,
                        CargoSpecificationDiff.FieldChange::after)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("目的地", "USNYC", "NLRTM"),
                        org.assertj.core.groups.Tuple.tuple("重量(kg)", "1200.00", "1500.00"));
    }

    @Test
    @DisplayName("桁が違うだけの数値は「変えた」と言わない")
    void ignoresScaleOnlyDifference() {
        // 投影は NUMERIC の位取りで返すので、1200 と 1200.00 が並ぶ。
        // equals で比べると、直していない重量が毎回差分に出る。
        CargoSnapshot after = new CargoSnapshot("JPTYO", "USNYC",
                LocalDate.of(2026, 12, 1), "GENERAL",
                new BigDecimal("1200"), null, null, null, 3, "自動車部品",
                null, null, null, null);

        assertThat(CargoSpecificationDiff.between(snapshot(), after)).isEmpty();
    }

    @Test
    @DisplayName("未入力になった項目も差分に出る（空欄と読める形で）")
    void reportsClearedFields() {
        CargoSnapshot before = new CargoSnapshot("JPTYO", "USNYC",
                LocalDate.of(2026, 12, 1), "HAZARDOUS",
                new BigDecimal("1200.00"), null, null, null, 3, "自動車部品",
                "3", "UN1993", null, null);

        assertThat(CargoSpecificationDiff.between(before, snapshot()))
                .extracting(CargoSpecificationDiff.FieldChange::label)
                .contains("IMO クラス", "国連番号");
        assertThat(CargoSpecificationDiff.between(before, snapshot()))
                .filteredOn(c -> c.label().equals("国連番号"))
                .extracting(CargoSpecificationDiff.FieldChange::after)
                .containsExactly("（未入力）");
    }

    @Test
    @DisplayName("比べる項目には全部ラベルがある（足し忘れたら落ちる）")
    void everyComparedFieldHasALabel() {
        // 名簿を手で書くので、要素を足したときに書き忘れると黙って差分から消える。
        // 通すのではなく落とす。載っていないものを通す名簿は、載せ忘れたものほど漏れる。
        for (String field : CargoSpecificationDiff.comparedFields()) {
            assertThat(CargoSpecificationDiff.labelOf(field)).isNotBlank();
        }
        assertThatThrownBy(() -> CargoSpecificationDiff.labelOf("知らない項目"))
                .isInstanceOf(IllegalStateException.class);
    }
}
