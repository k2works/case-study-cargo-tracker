package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.time.LocalDateTime;

/**
 * 経路の 1 区間（航海番号・積み地・降ろし地・日時）。bookingms の同名 VO と同一構造。
 */
public record Leg(
        String voyageNumber,
        Location loadLocation,
        Location unloadLocation,
        LocalDateTime loadTime,
        LocalDateTime unloadTime
) {}
