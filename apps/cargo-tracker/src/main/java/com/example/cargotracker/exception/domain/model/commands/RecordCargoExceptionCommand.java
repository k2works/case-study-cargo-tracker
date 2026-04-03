package com.example.cargotracker.exception.domain.model.commands;

import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;

import java.time.LocalDateTime;

/**
 * 貨物例外記録コマンド。
 */
public record RecordCargoExceptionCommand(
        String trackingNumber,
        ExceptionType exceptionType,
        String locationCode,
        LocalDateTime occurredAt,
        String reason
) {}
