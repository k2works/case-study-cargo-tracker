package com.example.billingms.domain.model;

/**
 * 精算明細（[ADR-027] 決定 6）。
 *
 * <p>料金調整（減額・補償費用）を基本料金に混ぜず、明細として積む——混ぜると
 * <strong>根拠が読めなくなる</strong>。減額は負、加算は正。
 *
 * <p><strong>内容の無い明細は作れない。</strong>金額だけ残ると、あとから誰も理由を言えない。
 *
 * @param description 調整の内容（根拠）
 * @param amount 金額
 */
public record InvoiceLineItem(String description, Money amount) {

    public InvoiceLineItem {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("調整の内容を入力してください");
        }
        if (amount == null) {
            throw new IllegalArgumentException("調整額を指定してください");
        }
    }

    public static InvoiceLineItem of(String description, Money amount) {
        return new InvoiceLineItem(description, amount);
    }
}
