package com.example.cargotracker.billing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 率を画面に出す百分率に変換する（US21 / US22。レビュー M6）。
 *
 * <p><strong>同じ変換に 2 つの答えを残さない。</strong> 割引率は 2 桁に揃えていたのに
 * 税率はそのまま 100 倍しており、<strong>「消費税（10.0000 %）」</strong>と表示されていた。
 * どちらも「率 → 画面に出す百分率」であり、変換は 1 か所に置く。
 *
 * <p><strong>切り捨てる。</strong> 表示のために率を大きく見せない。
 */
public final class Percentage {

    /** 画面に出す小数桁。 */
    private static final int SCALE = 2;

    private Percentage() {
    }

    /**
     * 率（{@code 0.1000}）を百分率（{@code 10.00}）にする。
     *
     * @param rate 率。{@code null} なら 0 とみなす
     */
    public static BigDecimal of(BigDecimal rate) {
        BigDecimal value = rate == null ? BigDecimal.ZERO : rate;
        return value.multiply(new BigDecimal("100")).setScale(SCALE, RoundingMode.DOWN);
    }
}
