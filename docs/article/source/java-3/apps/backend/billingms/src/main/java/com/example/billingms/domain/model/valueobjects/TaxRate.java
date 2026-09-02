package com.example.billingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 消費税率（[ADR-027] 決定 8）。
 *
 * <p><strong>扱う税区分は 2 つだけである</strong>（IT12・決定 8 の改訂）——課税（10%）と
 * <strong>輸出免税</strong>（0%）。出発地と目的地の国が異なる輸送は消費税が免除される。
 * IT11 は一律 10% で計算しており、これはキャンペーンではなく<strong>誤り</strong>だった。
 *
 * <p><strong>軽減税率は扱わない。</strong>対象になる貨物（食品）を判別する情報を持って
 * おらず、運賃には適用されない。任意の税率を入力する手段も置かない——置くと、
 * それが正しく使われているかを確かめる相手が要る。
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

    /**
     * 輸出免税（決定 8 の改訂）。
     *
     * <p>国際輸送の運賃は消費税が免除される。<strong>0% であることと、税率を
     * 決めていないことは違う</strong>——免税は判断の結果であり、
     * {@link #exempt()} が真かどうかで画面に税区分を出す。
     */
    public static TaxRate exempt() {
        return new TaxRate(BigDecimal.ZERO);
    }

    /**
     * 出発地と目的地から決める（決定 8 の改訂）。
     *
     * <p><strong>国が異なれば免税。</strong>国コードは地点マスタが持っている
     * （新しい列は要らない）。<strong>どちらかが不明なら課税に倒す</strong>
     * ——免税に倒すと、国コードを引けない不具合が「消費税を取り忘れる」形で出る。
     */
    public static TaxRate forRoute(String originCountry, String destinationCountry) {
        if (originCountry == null || destinationCountry == null) {
            return standard();
        }
        return originCountry.equals(destinationCountry) ? standard() : exempt();
    }

    /** 輸出免税か。**税区分として画面に出す**——「消費税 ¥0」だけでは計算漏れと読める。 */
    public boolean exempted() {
        return value.signum() == 0;
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
