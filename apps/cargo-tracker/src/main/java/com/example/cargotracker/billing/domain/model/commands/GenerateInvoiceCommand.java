package com.example.cargotracker.billing.domain.model.commands;

import java.time.LocalDate;

public record GenerateInvoiceCommand(String bookingId, String freightChargeId, LocalDate dueDate) {
    public GenerateInvoiceCommand {
        if (bookingId == null || bookingId.isBlank()) throw new IllegalArgumentException("予約 ID は必須です");
        if (freightChargeId == null || freightChargeId.isBlank()) throw new IllegalArgumentException("輸送料金 ID は必須です");
    }

    public GenerateInvoiceCommand(String bookingId, String freightChargeId) {
        this(bookingId, freightChargeId, null);
    }
}
