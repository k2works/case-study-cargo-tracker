package com.example.billingms.interfaces.rest.dto;

import com.example.billingms.domain.projections.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入金履歴応答 DTO（US23、IT8 T5.2 / ADR-0019、{@code GET /invoices/{id}/payments}）。
 *
 * <p>{@code paymentMethod} / {@code externalReference} は {@code PaymentDetailRecorded}
 * 補完 event 経由で UPDATE される。S23 詳細画面 / cross-service E2E で参照する。</p>
 */
public record PaymentResponse(
        String paymentId,
        String invoiceId,
        BigDecimal paidAmount,
        String currency,
        LocalDateTime paidAt,
        String paymentMethod,
        String externalReference
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getPaymentId(),
                p.getInvoiceId(),
                p.getPaidAmount(),
                p.getCurrency(),
                p.getPaidAt(),
                p.getPaymentMethod(),
                p.getExternalReference()
        );
    }
}
