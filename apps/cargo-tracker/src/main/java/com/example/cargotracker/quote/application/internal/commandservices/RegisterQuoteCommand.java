package com.example.cargotracker.quote.application.internal.commandservices;

import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積登録コマンド。
 */
public record RegisterQuoteCommand(
        String originLocode,
        String destinationLocode,
        LocalDate requestedArrivalDate,
        CargoType cargoType,
        BigDecimal weightKg
) {
}
