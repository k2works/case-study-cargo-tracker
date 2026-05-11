package com.example.billingms.application.internal.commandservices;

import java.util.List;

/**
 * 料金算出コマンド
 */
public record CalculateInvoiceCommand(
        String bookingId,
        String shipperId,
        List<LineItemInput> lineItems
) {
    public record LineItemInput(String description, long amountValue) {}
}
