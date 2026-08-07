package com.example.cargotracker.routing.infrastructure.repositories;

import java.math.BigDecimal;

/** 航海ごとの割当済み重量。 */
public class VoyageLoadRow {

    private String voyageNumber;
    private BigDecimal assignedWeight;

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public BigDecimal getAssignedWeight() {
        return assignedWeight;
    }

    public void setAssignedWeight(BigDecimal assignedWeight) {
        this.assignedWeight = assignedWeight;
    }
}
