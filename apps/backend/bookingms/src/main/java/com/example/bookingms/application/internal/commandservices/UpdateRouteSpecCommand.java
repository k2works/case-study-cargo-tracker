package com.example.bookingms.application.internal.commandservices;

import java.time.LocalDate;

/**
 * 経路条件再設定コマンド（アプリケーション層 DTO）
 */
public record UpdateRouteSpecCommand(
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline
) {}
