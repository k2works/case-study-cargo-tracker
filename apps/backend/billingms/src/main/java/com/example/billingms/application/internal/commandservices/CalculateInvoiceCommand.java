package com.example.billingms.application.internal.commandservices;

import java.math.BigDecimal;
import java.util.List;

/**
 * 料金算出コマンド
 */
public record CalculateInvoiceCommand(
        String bookingId,
        String shipperId,
        List<LineItemInput> lineItems,
        BigDecimal discountRate
) {
    public record LineItemInput(String description, long amountValue) {}
}
