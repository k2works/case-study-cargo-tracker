package com.example.billingms.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 消費税率（[ADR-027] 決定 8）。
 *
 * <p><strong>本 IT では既定の 10% だけを使う。</strong>税率を変える手段は置かない
 * ——置くと、それが正しく使われているかを確かめる相手（税区分・軽減税率・輸出免税）が
 * 要る。US23 で精算を扱うときに決める。
 *
 * <p>それでも型として持つのは、{@code invoice.tax_rate} が {@code NOT NULL} であり、
 * <strong>書かずには行を作れない</strong>ためである。
 *
 * @param value 税率
 */
public record TaxRate(BigDecimal value) {

    /** 既定の税率（決定 8）。 */
    private static final BigDecimal STANDARD = new BigDecimal("0.1000");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public TaxRate {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("税率は 0 以上で指定してください: " + value);
        }
    }

    public static TaxRate standard() {
        return new TaxRate(STANDARD);
    }

    public static TaxRate of(BigDecimal value) {
        return new TaxRate(value);
    }

    /** 課税額。**丸めは {@link Money} が行う**（決定 2）。 */
    public Money taxOf(Money base) {
        return base.multiply(value);
    }

    /** 百分率（画面表示用）。 */
    public BigDecimal percentage() {
        return value.multiply(HUNDRED).stripTrailingZeros().setScale(0, RoundingMode.HALF_UP);
    }
}
