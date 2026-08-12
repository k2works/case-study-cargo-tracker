package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 割引方針の種別（US22）。
 *
 * <p><strong>2 値だけである。</strong> 旧版は {@code VOLUME_DISCOUNT} /
 * {@code SEASONAL} を持っていたが、<strong>要求元のユーザーストーリーが無く</strong>
 * 削除した（`release_scope.md` のスコープ外。YAGNI）。
 */
public enum DiscountPolicyType {

    /** 法人契約の割引。**荷主ごとの契約割引率を適用する。** */
    CORPORATE_CONTRACT("法人契約割引"),

    /** 割引なし（個人荷主）。 */
    NONE("割引なし");

    private final String displayName;

    DiscountPolicyType(String displayName) {
        this.displayName = displayName;
    }

    /** 画面に出す日本語名。**列挙子名を利用者に見せない。** */
    public String displayName() {
        return displayName;
    }
}
