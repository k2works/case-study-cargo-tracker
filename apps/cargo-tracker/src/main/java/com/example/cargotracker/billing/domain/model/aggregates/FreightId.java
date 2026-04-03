package com.example.cargotracker.billing.domain.model.aggregates;

import java.util.UUID;

/**
 * 輸送料金 ID 値オブジェクト。
 */
public record FreightId(UUID value) {

    public FreightId {
        if (value == null) {
            throw new IllegalArgumentException("FreightId の値は null にできません");
        }
    }

    public static FreightId generate() {
        return new FreightId(UUID.randomUUID());
    }
}
