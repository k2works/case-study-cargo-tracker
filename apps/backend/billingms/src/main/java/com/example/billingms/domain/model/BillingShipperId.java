package com.example.billingms.domain.model;

/**
 * 荷主参照 ID。<strong>法人かどうかの判定を内包する</strong>（正典の要素表）。
 *
 * <p>割引が適用されるかは荷主の種別で決まる（US22）。判定をここに置くのは、
 * 呼び出し側が文字列を見比べると、同じ判断が複数の場所に散るためである。
 *
 * @param value 荷主 ID
 * @param corporate 法人か
 */
public record BillingShipperId(String value, boolean corporate) {

    public BillingShipperId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("荷主 ID を指定してください");
        }
    }

    public static BillingShipperId corporate(String value) {
        return new BillingShipperId(value, true);
    }

    public static BillingShipperId individual(String value) {
        return new BillingShipperId(value, false);
    }

    public boolean isCorporate() {
        return corporate;
    }
}
