package com.example.trackingms.application.internal.commandservices;

import java.time.LocalDate;

/**
 * 例外対応内容更新コマンド
 */
public record RespondToExceptionCommand(
        String trackingNumber,
        Long exceptionId,
        String responseContent,
        LocalDate newEstimatedArrival
) {}
