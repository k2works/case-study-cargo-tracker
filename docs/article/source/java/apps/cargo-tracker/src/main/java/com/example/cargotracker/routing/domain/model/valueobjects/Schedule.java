package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.routing.domain.model.CarrierMovement;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record Schedule(List<CarrierMovement> carrierMovements) {

    public Schedule {
        Objects.requireNonNull(carrierMovements, "carrierMovements must not be null");
        carrierMovements = Collections.unmodifiableList(carrierMovements);
    }

    public static Schedule of(List<CarrierMovement> movements) {
        return new Schedule(movements);
    }

    public boolean isEmpty() {
        return carrierMovements.isEmpty();
    }

    public int size() {
        return carrierMovements.size();
    }
}
