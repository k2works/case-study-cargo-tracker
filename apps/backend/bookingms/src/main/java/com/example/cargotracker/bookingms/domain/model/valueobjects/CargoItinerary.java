package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.util.List;
import java.util.Objects;

/**
 * 貨物の確定経路（Leg のリスト）。
 */
public record CargoItinerary(List<Leg> legs) {

    public CargoItinerary {
        Objects.requireNonNull(legs, "legs");
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("経路は 1 区間以上必要です");
        }
    }
}
