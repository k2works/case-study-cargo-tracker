package com.example.billingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 金額（[ADR-027](../../../../../../../../docs/adr/027-transport-charge-calculation.md) 決定 2）。
 *
 * <p><strong>丸めはここ 1 か所だけで行う。</strong>呼び出し側が丸めると、丸める場所が
 * 増えるたびに結果が変わりうる——画面と保存値が食い違い、明細の合計と総額が 1 円ずれる。
 *
 * <p><strong>丸めた結果を保持する。</strong>小数を抱えたまま持つと、表示のたびに
 * 丸め直すことになり、どこで丸めたのかが追えなくなる。
 *
 * <p><strong>通貨の違う金額は足せない。</strong>足せてしまうと、円とドルが混ざった
 * 請求書ができる。多通貨は将来の話だが、混ぜられる形にしておくと、混ざったことに
 * 気づけない。
 */
public final class Money {

    /** 円建ての通貨コード。 */
    public static final String JPY = "JPY";

    /**
     * 丸めの単位。<strong>1 円</strong>（決定 2）。
     *
     * <p>DB の列は {@code NUMERIC(15,2)} だが、保存するのは丸めたあとの値である
     * ——丸めの単位と保存の単位を一致させると、読んだときに何が起きたか分かる。
     */
    private static final int SCALE = 0;

    /**
     * 丸め方。<strong>四捨五入</strong>（{@code HALF_UP}）。
     *
     * <p>0 から遠いほうへ丸めるため、-100.5 は -101 になる。減額（負の明細）だけが
     * 有利／不利に倒れない。
     */
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    /** 円で金額を作る。**端数はここで丸まる。** */
    public static Money yen(BigDecimal amount) {
        return of(amount, JPY);
    }

    /** 通貨を指定して金額を作る。 */
    public static Money of(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("金額を指定してください");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException(
                    "通貨コードは 3 文字で指定してください: " + currency);
        }
        return new Money(amount.setScale(SCALE, ROUNDING), currency);
    }

    /** 0 円。 */
    public static Money zero() {
        return yen(BigDecimal.ZERO);
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return of(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return of(amount.subtract(other.amount), currency);
    }

    /**
     * 係数を掛ける。<strong>掛けてから丸める</strong>——丸めてから掛けると、
     * 係数が積み重なるほど誤差が開く。
     */
    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("係数を指定してください");
        }
        return of(amount.multiply(factor), currency);
    }

    /** 負の金額か。減額（明細）の判定に使う。 */
    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("金額を指定してください");
        }
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "通貨が違う金額は計算できません: " + currency + " と " + other.currency);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Money money)) {
            return false;
        }
        // **丸めたあとの値で比べる。** 100.4 円と 100.49 円はどちらも 100 円である
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
