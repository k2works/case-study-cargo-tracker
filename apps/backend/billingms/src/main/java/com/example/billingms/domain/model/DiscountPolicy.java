package com.example.billingms.domain.model;

/**
 * 割引方針（US22）。
 *
 * <p><strong>割引率は荷主に登録済みである</strong>（US03・IT2）。経理担当者が入力するのでは
 * ない——手で入れると、契約と違う率が入る。
 *
 * <p><strong>未設定は 0% ではない</strong>（[ADR-012]）。法人でも割引率が設定されていなければ
 * 割引は無い扱いにする。0% として扱うと、設定し忘れと「割引しない契約」が同じに見える。
 *
 * @param type 方針の種別
 * @param rate 割引率。割引が無いなら {@code null}
 */
public record DiscountPolicy(DiscountPolicyType type, DiscountRate rate) {

    /** 法人契約の割引。**率が未設定なら割引なしになる**。 */
    public static DiscountPolicy forCorporate(DiscountRate rate) {
        return rate == null ? none() : new DiscountPolicy(DiscountPolicyType.CORPORATE_STANDARD, rate);
    }

    /** 割引なし（個人荷主・率が未設定の法人）。 */
    public static DiscountPolicy none() {
        return new DiscountPolicy(DiscountPolicyType.NONE, null);
    }

    /** 方針の種別から作る。 */
    public static DiscountPolicy of(DiscountPolicyType type, DiscountRate rate) {
        return switch (type) {
            case CORPORATE_STANDARD -> forCorporate(rate);
            case NONE -> none();
        };
    }

    /**
     * 割引額。
     *
     * <p><strong>丸めは {@link Money} が行う</strong>（[ADR-027] 決定 2）。ここで丸めると、
     * 丸める場所が 2 か所になる。
     */
    public Money discountOf(Money baseAmount) {
        return rate == null ? Money.zero() : baseAmount.multiply(rate.value());
    }

    /** 割引が適用されるか。 */
    public boolean applies() {
        return rate != null;
    }
}
