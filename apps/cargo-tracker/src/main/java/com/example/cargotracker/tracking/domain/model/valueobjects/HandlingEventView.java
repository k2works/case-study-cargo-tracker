package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.time.LocalDateTime;

/**
 * 追跡コンテキストから見た荷役イベントのサマリー値オブジェクト。
 */
public record HandlingEventView(
        LocalDateTime completionTime,
        String locationCode,
        String eventType,
        String memo
) {}
