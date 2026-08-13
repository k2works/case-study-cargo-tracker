package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 請求書の種別（US30。ADR-020）。
 *
 * <p><strong>1 つの予約に 2 種類の請求書が並びうる。</strong> 輸送中の貨物を
 * キャンセルすると、すでに発行した輸送料金の請求書とは別に、キャンセル料の
 * 請求書が生まれる。V1 は「予約ごとに 1 枚」で二重請求を防いでいたが、
 * <strong>その防ぎ方ではキャンセル料を請求できない</strong>。
 *
 * <p><strong>種別を分ける理由は金額の性質が違うことである。</strong>
 * 輸送料金は運んだことへの対価であり、キャンセル料は運ばなかったことへの
 * 対価である。同じ列に混ぜると、月次の締めで「輸送で得た売上」を数えられない。
 *
 * <p><strong>{@code ChargeStatus} / {@code PaymentStatus} と混ぜない</strong>
 * （ADR-017 と同じ判断）。あれらは請求書の進み方であり、こちらは請求書が
 * 何の対価かである。
 */
public enum InvoiceType {

    /** 輸送料金（US21 / US22）。<strong>運んだことへの対価である。</strong> */
    TRANSPORT("輸送料金", "bg-primary"),

    /** キャンセル料（US30）。<strong>運ばなかったことへの対価である。</strong> */
    CANCELLATION("キャンセル料", "bg-warning text-dark");

    private final String displayName;
    private final String badgeClass;

    InvoiceType(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    /** 画面に出す日本語名。<strong>列挙子名を利用者に見せない。</strong> */
    public String displayName() {
        return displayName;
    }

    /** 画面のバッジ。<strong>正典はここである</strong> — 画面で色を決め直さない。 */
    public String badgeClass() {
        return badgeClass;
    }

    /** キャンセル料か。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean isCancellation() {
        return this == CANCELLATION;
    }

    /**
     * 保存された値から復元する。
     *
     * <p><strong>列が無かったころの行は輸送料金である</strong>
     * （キャンセル料はこの列と同時に生まれた）。読めない値で例外にすると、
     * その請求書の画面ごと開けなくなる。
     */
    public static InvoiceType ofRestored(String value) {
        if (value == null || value.isBlank()) {
            return TRANSPORT;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return TRANSPORT;
        }
    }
}
