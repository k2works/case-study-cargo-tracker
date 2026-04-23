package com.example.cargotracker.billing.application.internal.commandservices;

public record CalculateFreightCommand(
        String bookingId,
        int adjustmentAmount,
        String adjustmentReason
) {
}
