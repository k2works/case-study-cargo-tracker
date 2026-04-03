package com.example.cargotracker.exception.domain.model.aggregates;

import java.util.UUID;

/**
 * 貨物例外 ID 値オブジェクト。
 */
public record ExceptionId(UUID value) {

    public ExceptionId {
        if (value == null) {
            throw new IllegalArgumentException("ExceptionId の値は null にできません");
        }
    }

    public static ExceptionId generate() {
        return new ExceptionId(UUID.randomUUID());
    }
}
