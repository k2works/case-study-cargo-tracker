package com.example.cargotracker.billing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;

/**
 * 荷主の種別（US22）。
 *
 * <p><b>割り引くかどうかは列挙が答える。</b> 呼ぶ側の if で書くと、呼ぶ場所が
 * 増えたぶんだけ抜ける。</p>
 *
 * <p>bookingms にも同じ名前の概念があるが、これは billingms の型である
 * （契約に列挙型を載せず、BC ごとに組み直す）。</p>
 */
public enum ShipperType {

    /** 法人。契約割引率が当たる。 */
    CORPORATE("法人", true),

    /** 個人。<b>割引は当たらない</b>（US22 §受入基準 3）。 */
    INDIVIDUAL("個人", false);

    private final String label;
    private final boolean discountable;

    ShipperType(String label, boolean discountable) {
        this.label = label;
        this.discountable = discountable;
    }

    public String label() {
        return label;
    }

    /** 契約割引が当たる種別か。 */
    public boolean discountable() {
        return discountable;
    }

    /** スナップショットに写っている名前から組み直す。 */
    public static ShipperType of(String name) {
        if (name == null) {
            throw new BusinessRuleViolation("荷主種別が分かりません");
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleViolation("知らない荷主種別です: " + name);
        }
    }
}
