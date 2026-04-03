package com.example.cargotracker.billing.domain.model.commands;

/**
 * 輸送料金算出コマンド。
 */
public record CalculateFreightCommand(String bookingId) {
    public CalculateFreightCommand {
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("予約 ID は null または空白にできません");
        }
    }
}
