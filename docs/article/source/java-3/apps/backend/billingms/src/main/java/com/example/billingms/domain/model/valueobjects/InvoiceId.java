package com.example.billingms.domain.model.valueobjects;

/**
 * 請求番号（[ADR-027] 注 7）。
 *
 * <p><strong>DB の {@code id} ではない。</strong>採番された業務上の番号であり、予約の
 * {@code BookingId} と同じ形（[ADR-011]）。荷主に伝えるのはこちらである。
 *
 * @param value 請求番号
 */
public record InvoiceId(String value) {

    public InvoiceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("請求番号を指定してください");
        }
    }

    public static InvoiceId of(String value) {
        return new InvoiceId(value);
    }
}
