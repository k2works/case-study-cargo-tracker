package com.example.billingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 契約割引率（US22・正典のビジネスルール 4）。
 *
 * <p>値域は 0〜30%。<strong>30% を超える割引は契約に無い</strong>——通すと、入力の誤りが
 * そのまま請求額になる。
 *
 * <p><strong>「未設定」はこの型では表さない。</strong>未設定は {@code null} であり、
 * 0% ではない（[ADR-012]）。0% として扱うと、設定し忘れと「割引しない契約」が
 * 同じに見える。
 *
 * @param value 割引率（0.0000〜0.3000）
 */
public record DiscountRate(BigDecimal value) {

    /** 上限。正典のビジネスルール 2「最大 30% の割引」。 */
    private static final BigDecimal MAX = new BigDecimal("0.3000");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public DiscountRate {
        if (value == null) {
            throw new IllegalArgumentException("割引率を指定してください");
        }
        if (value.signum() < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException(
                    "割引率は 0% 以上 30% 以下で指定してください: " + value);
        }
    }

    public static DiscountRate of(BigDecimal value) {
        return new DiscountRate(value);
    }

    /**
     * 百分率。
     *
     * <p>画面は<strong>率そのものを出す</strong>（22-4）——額だけでは率を復元できない。
     */
    public BigDecimal percentage() {
        return value.multiply(HUNDRED).stripTrailingZeros().setScale(0, RoundingMode.HALF_UP);
    }
}
