package com.example.cargotracker.tracking.infrastructure.repositories;

import java.time.LocalDateTime;

/**
 * 追跡クエリで使用する荷役イベントのレコード（読み取り専用）。
 */
public record HandlingEventRecord(
        LocalDateTime completionTime,
        String locationCode,
        String eventType,
        String memo
) {}
