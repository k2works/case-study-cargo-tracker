package com.example.cargotracker.shipper.domain.model;

import java.math.BigDecimal;

/**
 * 契約割引率（US03 / US22）。
 *
 * <p><strong>値域 0.0000〜0.3000（0%〜30%）はドメインの不変条件である</strong>
 * （{@code domain-model.md} ビジネスルール 4）。
 *
 * <p><strong>画面に別の上限を書かない。</strong> 旧版の割引ポリシー画面は
 * -50〜100% を許容しており、<strong>画面から入力できてドメインが弾く</strong>状態だった
 * （{@code ui_design.md}）。上限が 2 か所にあると、どちらが正なのか分からなくなる。
 *
 * <p><strong>負の割引率は「割増」である。</strong> それは割引ではない。
 * 値上げを割引率で表すと、請求書に「割引 -10%」と印字される。
 *
 * @param value 割引率（0.0000〜0.3000）
 */
public record DiscountRate(BigDecimal value) {

    /** 上限 30%。DB の {@code chk_shipper_discount_rate} と同じ値である。 */
    private static final BigDecimal MAX = new BigDecimal("0.3000");

    public DiscountRate {
        if (value == null) {
            throw new IllegalArgumentException("契約割引率は必須です");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("契約割引率に負の値は指定できません");
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("契約割引率は 30% を超えられません");
        }
    }

    /** 割引なし（契約はあるが割引率が 0 の法人）。 */
    public static DiscountRate none() {
        return new DiscountRate(BigDecimal.ZERO);
    }

    /**
     * 画面に出す百分率（{@code 10.00} 形式）。
     *
     * <p><strong>画面で計算しない。</strong> 表示のたびに 100 倍する式を書くと、
     * 桁の丸め方が画面ごとに違ってくる。
     */
    public BigDecimal asPercentage() {
        return value.multiply(new BigDecimal("100")).stripTrailingZeros();
    }
}
