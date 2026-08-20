package com.example.bookingms.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 法人契約の割引率。
 *
 * <p>DB は割合（0.1250）、画面は百分率（12.5%）で扱う。変換を呼び出し側それぞれに書くと、
 * どこか 1 箇所だけ 100 倍された値が保存される。変換はこの型の中だけで行う。
 */
public final class DiscountRate {

    /** 上限。これを超える割引は契約ではなく入力の誤りとみなす。 */
    private static final BigDecimal MAX_PERCENT = new BigDecimal("30");

    private static final BigDecimal MIN_PERCENT = BigDecimal.ZERO;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 割合（0.0000〜0.3000）。DB の NUMERIC(5,4) に合わせる。 */
    private final BigDecimal rate;

    private DiscountRate(BigDecimal rate) {
        this.rate = rate;
    }

    /** 百分率（12.5 は 12.5%）から生成する。 */
    public static DiscountRate ofPercent(BigDecimal percent) {
        if (percent == null) {
            throw new IllegalArgumentException("割引率は必須です");
        }
        if (percent.compareTo(MIN_PERCENT) < 0 || percent.compareTo(MAX_PERCENT) > 0) {
            throw new IllegalArgumentException("割引率は 0〜30% の範囲で指定してください: " + percent);
        }
        return new DiscountRate(percent.divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }

    /** 永続化された割合（0.1250）から生成する。 */
    public static DiscountRate ofRate(BigDecimal rate) {
        if (rate == null) {
            throw new IllegalArgumentException("割引率は必須です");
        }
        return ofPercent(rate.multiply(HUNDRED));
    }

    public BigDecimal rate() {
        return rate;
    }

    public BigDecimal percent() {
        return rate.multiply(HUNDRED).stripTrailingZeros();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DiscountRate discountRate && rate.compareTo(discountRate.rate) == 0;
    }

    @Override
    public int hashCode() {
        return rate.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return percent().toPlainString() + "%";
    }
}
