package com.example.cargotracker.billing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金額（billingms の値オブジェクト）。
 *
 * <p><b>共有カーネルには置かない</b>（{@code domain-model.md}「置かないもの」）。
 * BC ごとに通貨も丸めも違いうる。営業日（{@code HolidayCalendar}）を共有カーネルへ
 * 移すのとは逆向きの判断だが、理由が違う——営業日は<b>全 BC で同じでなければ
 * ならない</b>のに対し、金額の型はそうではない。</p>
 *
 * <p><b>丸めはここ 1 か所だけ</b>（正典の料金計算）。各所で {@code setScale} を書くと、
 * 足す順で合計が変わる。計算の途中は丸めず、請求書に載せるときに
 * {@link #roundToUnit()} で 1 円単位にする。</p>
 *
 * <p><b>通貨が違う金額は足さない</b>（不変条件 1「通貨は集約内で一貫」）。</p>
 *
 * @param amount 金額。<b>負にならない</b>——請求書に負の基本料金は無い
 * @param currency ISO 4217（この版は {@code JPY} だけを使う）
 */
public record Money(BigDecimal amount, String currency) {

    /** 計算の途中で持つ桁。<b>ここでは丸めない。</b> */
    private static final int SCALE = 4;

    public static final String JPY = "JPY";

    public Money {
        if (amount == null) {
            throw new BusinessRuleViolation("金額がありません");
        }
        if (currency == null || currency.isBlank()) {
            throw new BusinessRuleViolation("通貨がありません");
        }
        if (amount.signum() < 0) {
            // **黙って 0 にしない。** 割引が基本料金を超えたなら、それは式か
            // 入力の誤りで、0 円の請求書として出してよいものではない。
            throw new BusinessRuleViolation("金額が負になりました: " + amount + " " + currency);
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 円。この版の請求はすべて円建てである。 */
    public static Money yen(BigDecimal amount) {
        return new Money(amount, JPY);
    }

    /** 0 円。 */
    public static Money zero() {
        return yen(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new BusinessRuleViolation("掛ける係数がありません");
        }
        return new Money(amount.multiply(factor), currency);
    }

    /** <b>1 円単位で四捨五入する。</b> 丸めるのはここだけ（正典）。 */
    public Money roundToUnit() {
        return new Money(amount.setScale(0, RoundingMode.HALF_UP), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        if (other == null) {
            throw new BusinessRuleViolation("比べる金額がありません");
        }
        if (!currency.equals(other.currency)) {
            throw new BusinessRuleViolation(
                    "通貨が違う金額は計算できません: " + currency + " と " + other.currency);
        }
    }

    @Override
    public String toString() {
        return roundToUnit().amount().toPlainString() + " " + currency;
    }
}
