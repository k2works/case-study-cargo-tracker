package com.example.billingms.interfaces.rest.dto;

import com.example.billingms.domain.projections.InvoiceLine;

import java.math.BigDecimal;

/**
 * 請求書明細行レスポンス DTO（US21 / US22 / IT7 タスク 2.5）。
 */
public record InvoiceLineResponse(
        int lineSeq,
        String lineType,
        String description,
        BigDecimal amount,
        String reasonCode
) {

    public static InvoiceLineResponse from(InvoiceLine line) {
        return new InvoiceLineResponse(
                line.getLineSeq(),
                line.getLineType(),
                line.getDescription(),
                line.getAmount(),
                line.getReasonCode()
        );
    }
}
