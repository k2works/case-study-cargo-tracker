package com.example.cargotracker.handling.domain.model.aggregates;

import java.util.UUID;

/**
 * 荷役イベント ID 値オブジェクト。
 */
public record HandlingEventId(UUID value) {

    public HandlingEventId {
        if (value == null) {
            throw new IllegalArgumentException("HandlingEventId の値は null にできません");
        }
    }

    public static HandlingEventId generate() {
        return new HandlingEventId(UUID.randomUUID());
    }
}
