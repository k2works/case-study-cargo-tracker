package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 支払方法（US23）。
 *
 * <p><strong>入金の照合先を表す。</strong> 銀行振込は入金明細、
 * クレジットカードは決済番号と突き合わせる。
 * <strong>表示名をここに置く</strong> — 画面が列挙子名を書き写すと、
 * 種別を足したときに片方だけ古くなる。
 */
public enum PaymentMethod {

    /** 銀行振込。 */
    BANK_TRANSFER("銀行振込"),

    /** クレジットカード。 */
    CREDIT_CARD("クレジットカード");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    /** 画面に出す日本語名。<strong>列挙子名を利用者に見せない。</strong> */
    public String displayName() {
        return displayName;
    }

    /**
     * 名前から引く。
     *
     * <p><strong>読めない値を既定へ寄せない。</strong> 支払方法が分からない入金は、
     * 帳簿の照合ができない。
     */
    public static PaymentMethod of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("支払方法は必須です");
        }
        try {
            return valueOf(name.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("支払方法が不正です: " + name);
        }
    }
}
