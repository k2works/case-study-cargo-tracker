package com.example.cargotracker.billing.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 請求で扱う金額（US21。{@code domain-model.md}「金額の丸め規則」）。
 *
 * <p><strong>金額計算は法的・会計的な争いの対象になりうる。</strong> 丸めの規則と
 * 適用順序を仕様として固定する。順序が決まっていないと、
 * <strong>同じ入力でも実装者によって請求額が変わる</strong>。
 *
 * <table>
 *   <caption>丸め規則</caption>
 *   <tr><td>丸めモード</td><td>切り捨て（{@link RoundingMode#DOWN}）。
 *       <strong>荷主に不利な方向へ丸めない</strong></td></tr>
 *   <tr><td>丸めの単位</td><td>通貨の最小単位。<strong>最小通貨単位の整数で保持する</strong></td></tr>
 *   <tr><td>適用箇所</td><td>基本料金・割引後料金・消費税額の<strong>それぞれで丸める</strong>
 *       （段階丸め）。総額での一括丸めは行わない</td></tr>
 *   <tr><td>中間計算</td><td>丸める直前まで {@code BigDecimal} で保持する。
 *       {@code double} を使わない</td></tr>
 * </table>
 *
 * <p><strong>Routing の {@code Money} とは別の型である。</strong> 概算費用（ADR-008）は
 * 経路候補の並べ替え用であり、請求額ではない。BC をまたいで型を共有すると、
 * <strong>並べ替えの物差しが請求に流れ込む</strong>（ADR-005）。
 *
 * @param value    金額（最小通貨単位の整数）
 * @param currency 通貨コード（ISO 4217）
 */
public record Money(BigDecimal value, String currency) {

    /** 本システムの既定通貨。 */
    public static final String JPY = "JPY";

    /**
     * 中間計算のスケール。
     *
     * <p><strong>丸める直前までは落とさない。</strong> 途中で丸めると、
     * 段階丸めの各段で二重に丸めたのと同じ結果になる。
     */
    private static final int CALC_SCALE = 10;

    public Money {
        if (value == null) {
            throw new IllegalArgumentException("金額は必須です");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("通貨は必須です");
        }
        if (value.signum() < 0) {
            // **負の金額は請求ではない。** 返金は精算の取り消しを伴う別の業務である
            throw new IllegalArgumentException("請求の金額は負にできません: " + value);
        }
        value = value.setScale(0, RoundingMode.DOWN);
        currency = currency.strip().toUpperCase(Locale.ROOT);
    }

    /** 円で作る。 */
    public static Money yen(BigDecimal value) {
        return new Money(value, JPY);
    }

    /** 円のゼロ。 */
    public static Money zeroYen() {
        return yen(BigDecimal.ZERO);
    }

    /**
     * 係数を掛けて<strong>丸める</strong>（段階丸めの 1 段）。
     *
     * <p>割引の適用（{@code × (1 - 割引率)}）と消費税の算出（{@code × 税率}）で使う。
     * <strong>掛けるたびに丸める</strong>のが仕様である。
     */
    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("係数は必須です");
        }
        return new Money(
                value.setScale(CALC_SCALE, RoundingMode.UNNECESSARY).multiply(factor),
                currency);
    }

    /** 足す。<strong>通貨が違えば足さない。</strong> */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(value.add(other.value), currency);
    }

    /**
     * 引く。
     *
     * <p><strong>差し引いて負になる減額は認めない。</strong> 請求額を超える減額は
     * 「返金」であり、精算の取り消しを伴う別の業務である
     * （`release_scope.md` のスコープ外。Release 2.0 で判断する）。
     * <strong>黙って負の請求書を作らない。</strong>
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        BigDecimal result = value.subtract(other.value);
        if (result.signum() < 0) {
            throw new IllegalArgumentException(
                    "請求額を超える減額はできません（返金は別の業務です）: %s - %s"
                            .formatted(value, other.value));
        }
        return new Money(result, currency);
    }

    /** ゼロか。 */
    public boolean isZero() {
        return value.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("金額は必須です");
        }
        if (!currency.equals(other.currency)) {
            // **多通貨は保留である**（`release_scope.md`）。
            // 型が黙って足すと、保留のはずのものが動いてしまう
            throw new IllegalArgumentException(
                    "通貨が異なる金額は計算できません: %s と %s".formatted(currency, other.currency));
        }
    }
}
