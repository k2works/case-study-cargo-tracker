package com.example.cargotracker.billing.domain.model.commands;

import java.math.BigDecimal;

/**
 * 輸送料金算出コマンド。
 */
public record CalculateFreightCommand(String bookingId, BigDecimal adjustmentAmount) {
    public CalculateFreightCommand {
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("予約 ID は null または空白にできません");
        }
    }
}
