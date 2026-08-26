package com.example.billingms.domain.model;

/**
 * 請求先の荷主（正典の要素表「法人判定を内包」）。
 *
 * <p><strong>発行した時点の社名を持つ。</strong>荷主 ID から毎回引き直すと、社名を変えた
 * 途端に発行済みの請求書の宛名まで変わる——出した書面が後から書き換わるのは、
 * [ADR-027] 決定 4 が禁じていることと同じである。
 *
 * <p>割引が適用されるかは荷主の種別で決まる（US22）。判定をここに置くのは、呼び出し側が
 * 文字列を見比べると、同じ判断が複数の場所に散るためである。
 *
 * @param value 荷主 ID
 * @param name 発行した時点の社名
 * @param corporate 法人か
 */
public record BillingShipperId(String value, String name, boolean corporate) {

    public BillingShipperId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("荷主 ID を指定してください");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("荷主の社名を指定してください");
        }
    }

    public static BillingShipperId corporate(String value, String name) {
        return new BillingShipperId(value, name, true);
    }

    public static BillingShipperId individual(String value, String name) {
        return new BillingShipperId(value, name, false);
    }

    public boolean isCorporate() {
        return corporate;
    }
}
