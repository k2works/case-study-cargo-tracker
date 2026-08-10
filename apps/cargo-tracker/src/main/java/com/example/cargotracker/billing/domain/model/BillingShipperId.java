package com.example.cargotracker.billing.domain.model;

/**
 * 荷主への参照（US22）。
 *
 * <p><strong>法人判定を内包する</strong>（{@code domain-model.md} の要素表）。
 * 割引を適用するかどうかは荷主種別で決まるため、参照 ID と一緒に運ぶ。
 * <strong>種別を別々に持ち回ると、片方だけを渡し忘れる。</strong>
 *
 * @param value     荷主 ID（UUID の文字列表現）
 * @param corporate 法人荷主か
 */
public record BillingShipperId(String value, boolean corporate) {

    public BillingShipperId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("荷主 ID は必須です");
        }
        value = value.strip();
    }

    /** 法人か。**割引の可否は本述語で決める。** */
    public boolean isCorporate() {
        return corporate;
    }
}
