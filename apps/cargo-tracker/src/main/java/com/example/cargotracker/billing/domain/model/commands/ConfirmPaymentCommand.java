package com.example.cargotracker.billing.domain.model.commands;

public record ConfirmPaymentCommand(String invoiceId) {
    public ConfirmPaymentCommand {
        if (invoiceId == null || invoiceId.isBlank()) throw new IllegalArgumentException("精算書 ID は必須です");
    }
}
