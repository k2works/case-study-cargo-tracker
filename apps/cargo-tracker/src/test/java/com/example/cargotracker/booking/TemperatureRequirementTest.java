package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.TemperatureUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 温度管理条件の境界（US05 / IT12 の C6）。
 *
 * <p>IT9 のレビューが<strong>温度の境界（{@code min == max}）が未検証</strong>だと
 * 指摘し、3 イテレーション繰り越した。
 *
 * <p><strong>「必須である」だけを確かめると、何を書いても通る。</strong>
 * 上下の大小だけを見る検査は、桁を打ち間違えた {@code -999} を通す。
 */
@DisplayName("温度管理条件の境界（C6）")
class TemperatureRequirementTest {


    /**
     * <strong>上下が同じ指定は通す。</strong>
     *
     * <p>定温輸送（医薬品・精密機器）は実務にある。
     * <strong>「範囲」という語に引きずって上下を必ず離すと、
     * 実在する輸送が登録できなくなる。</strong>
     */
    @Test
    void 上下が同じ温度は定温輸送として通る() {
        TemperatureRequirement constant = new TemperatureRequirement(
                new BigDecimal("2.0"), new BigDecimal("2.0"), TemperatureUnit.CELSIUS);

        assertThat(constant.display()).contains("2.0");
    }

    /** 入れ違いは打ち間違いとして日常的に起きる。**通すと条件を満たせない貨物を預かる。** */
    @Test
    void 上下が入れ違った指定は通らない() {
        assertThatThrownBy(() -> new TemperatureRequirement(
                new BigDecimal("5.0"), new BigDecimal("-5.0"), TemperatureUnit.CELSIUS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上回っています");
    }

    /**
     * <strong>絶対零度より低い温度は物理的に存在しない。</strong>
     *
     * <p>{@code -999} は桁の打ち間違いとして起きる。上下の大小だけを見る検査は
     * これを通し、<strong>どの設備でも守れない条件の貨物を預かる</strong>ことになる。
     */
    @Test
    void 絶対零度を下回る温度は通らない() {
        assertThatThrownBy(() -> new TemperatureRequirement(
                new BigDecimal("-999"), new BigDecimal("-18"), TemperatureUnit.CELSIUS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("絶対零度");
    }

    /** 華氏でも同じ守りが働く。**単位ごとに絶対零度は違う。** */
    @Test
    void 華氏でも絶対零度を下回る温度は通らない() {
        assertThatThrownBy(() -> new TemperatureRequirement(
                new BigDecimal("-500"), new BigDecimal("0"), TemperatureUnit.FAHRENHEIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("絶対零度");

        // 摂氏の下限（-273.15）は華氏では有効な温度である。
        // **単位を見ずに一律で弾く実装で緑にしない**
        assertThat(new TemperatureRequirement(
                new BigDecimal("-300"), new BigDecimal("-100"), TemperatureUnit.FAHRENHEIT)
                .display()).contains("-300");
    }
}
