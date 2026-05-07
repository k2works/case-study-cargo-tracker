package com.example.routingms.domain.model.valueobjects;

import com.example.routingms.domain.model.entities.CarrierMovement;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Schedule {

    private final List<CarrierMovement> carrierMovements;

    public Schedule(List<CarrierMovement> carrierMovements) {
        if (carrierMovements == null || carrierMovements.isEmpty()) {
            throw new IllegalArgumentException("Schedule must have at least one CarrierMovement");
        }
        this.carrierMovements = List.copyOf(carrierMovements);
    }

    public List<CarrierMovement> getCarrierMovements() {
        return Collections.unmodifiableList(carrierMovements);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Schedule that)) return false;
        return Objects.equals(carrierMovements, that.carrierMovements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carrierMovements);
    }
}
