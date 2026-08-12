package com.example.cargotracker.billing.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 適用する割引率（US22）。
 *
 * <p><strong>値域 0.0000〜0.3000（0%〜30%）はドメインの不変条件である</strong>
 * （{@code domain-model.md} ビジネスルール 4）。<strong>画面に別の上限を書かない。</strong>
 * 上限が 2 か所にあると、どちらが正なのか分からなくなる。
 *
 * <p><strong>Shipper Context の {@code DiscountRate} とは別の型である。</strong>
 * あちらは「契約に書かれた率」、こちらは「この請求書に適用した率」である。
 * BC をまたいで運べるのは素の値だけであり（ADR-005）、型そのものは渡せない。
 *
 * <p><strong>割引なしは 0% で表す。</strong> {@code null} で表すと、
 * 「割引が無い」と「まだ決めていない」が同じ形になる。
 *
 * @param value 割引率（0.0000〜0.3000）
 */
public record DiscountRate(BigDecimal value) {

    /** 上限 30%。DB の {@code chk_shipper_discount_rate} と同じ値である。 */
    private static final BigDecimal MAX = new BigDecimal("0.3000");

    /** 割引率のスケール（{@code invoice.discount_rate} は NUMERIC(5,4)）。 */
    private static final int SCALE = 4;

    public DiscountRate {
        if (value == null) {
            throw new IllegalArgumentException("割引率は必須です");
        }
        if (value.signum() < 0) {
            // **負の割引率は「割増」である。** 請求書に「割引 -10%」と印字される
            throw new IllegalArgumentException("割引率に負の値は指定できません: " + value);
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("割引率は 30% を超えられません: " + value);
        }
        value = value.setScale(SCALE, RoundingMode.DOWN);
    }

    /** 率から作る。 */
    public static DiscountRate of(BigDecimal value) {
        return new DiscountRate(value);
    }

    /** 割引なし（0%）。 */
    public static DiscountRate none() {
        return new DiscountRate(BigDecimal.ZERO);
    }

    /** 割引が無いか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean isNone() {
        return value.signum() == 0;
    }

    /**
     * 割引後の係数（{@code 1 - 割引率}）。
     *
     * <p><strong>引き算を計算側や画面で書き直さない。</strong> 2 か所に書くと、
     * 片方だけが「割増」の符号を取り違える。
     */
    public BigDecimal discountFactor() {
        return BigDecimal.ONE.subtract(value);
    }

    /**
     * 画面に出す百分率（{@code 15.00}）。
     *
     * <p><strong>変換は {@link Percentage} に集約する</strong>（レビュー M6）。
     * 税率も同じ変換を通る。<strong>同じ問題に 2 つの答えを残さない。</strong>
     */
    public BigDecimal asPercent() {
        return Percentage.of(value);
    }
}
