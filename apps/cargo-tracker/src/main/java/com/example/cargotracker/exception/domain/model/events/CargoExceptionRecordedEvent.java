package com.example.cargotracker.exception.domain.model.events;

import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;

/**
 * 貨物例外記録ドメインイベント。
 */
public record CargoExceptionRecordedEvent(
        ExceptionId exceptionId,
        String trackingNumber,
        ExceptionType exceptionType,
        boolean urgent
) {}
