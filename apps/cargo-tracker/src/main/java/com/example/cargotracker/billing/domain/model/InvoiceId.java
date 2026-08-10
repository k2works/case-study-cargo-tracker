package com.example.cargotracker.billing.domain.model;

/**
 * 精算書の識別子（US21）。
 *
 * <p>永続化では業務キー {@code invoice.invoice_number} に対応する。
 *
 * @param value 精算書番号
 */
public record InvoiceId(String value) {

    public InvoiceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("請求番号は必須です");
        }
        value = value.strip();
    }

    public static InvoiceId of(String value) {
        return new InvoiceId(value);
    }
}
