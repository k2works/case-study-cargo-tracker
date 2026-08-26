package com.example.billingms.domain.model;

/**
 * 入金の方法（US23-3・[ADR-028] 決定 2）。
 *
 * <p><strong>決済機関とは連携していない。</strong>経理担当者が通帳や入金明細を見て
 * 手で記録する（代替）。方法を残すのは、あとから照合するときに
 * 「どこを見れば裏が取れるか」が分かるようにするためである。
 */
public enum PaymentMethod {

    /** 銀行振込。**参照番号は振込の照会番号**。 */
    BANK_TRANSFER("銀行振込"),

    /** 手形。 */
    PROMISSORY_NOTE("手形"),

    /** 相殺。**現金は動かない**——請求と債務を突き合わせて消す。 */
    OFFSET("相殺"),

    /** その他。**理由は参照番号に書く。** */
    OTHER("その他");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    /** 画面に出す名前。 */
    public String label() {
        return label;
    }

    /**
     * 文字列から引く。
     *
     * <p><strong>知らない方法は断る。</strong>既定値に倒すと、画面の選択肢を増やした
     * ときに、その方法で入金した記録がすべて「銀行振込」として残る。
     */
    public static PaymentMethod of(String name) {
        if (name == null) {
            throw new IllegalArgumentException("入金の方法を指定してください");
        }
        for (PaymentMethod method : values()) {
            if (method.name().equals(name)) {
                return method;
            }
        }
        throw new IllegalArgumentException("扱いを決めていない入金の方法です: " + name);
    }
}
