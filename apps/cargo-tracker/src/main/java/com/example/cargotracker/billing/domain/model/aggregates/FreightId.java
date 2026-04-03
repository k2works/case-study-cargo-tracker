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

    public static FreightId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FreightId の文字列値は null または空にできません");
        }
        try {
            return new FreightId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("FreightId の形式が不正です: " + value, e);
        }
    }
}
