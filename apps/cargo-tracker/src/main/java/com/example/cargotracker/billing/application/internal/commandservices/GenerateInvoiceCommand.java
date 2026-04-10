package com.example.cargotracker.billing.application.internal.commandservices;

public record GenerateInvoiceCommand(
        String bookingId,
        String shipperId,
        int baseAmountValue
) {
}
