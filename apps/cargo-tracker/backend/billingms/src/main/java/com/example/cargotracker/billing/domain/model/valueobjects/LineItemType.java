package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 明細行の種別（{@code invoice_line_item.item_type}）。
 *
 * <p><b>表示の分類であって業務判断ではない。</b> 金額の計算はどの行も同じで、
 * 種別は S61 が並べ方と文言を決めるために使う。</p>
 */
public enum LineItemType {

    /** 基本料金。 */
    BASE("基本料金"),

    /** 法人割引（US22）。 */
    DISCOUNT("割引"),

    /** 調整（減額・補償費用。US21 §受入基準 6）。 */
    ADJUSTMENT("調整"),

    /** キャンセル料（US30・IT15）。 */
    CANCELLATION_FEE("キャンセル料"),

    /** 消費税。 */
    TAX("消費税");

    private final String label;

    LineItemType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
