package com.example.cargotracker.estimation.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** {@code estimate} テーブルの行。 */
public class EstimateRecord {

    private long id;
    private UUID estimateId;
    private String origin;
    private String destination;
    private LocalDate arrivalDeadline;
    private String cargoType;
    private BigDecimal weightKg;
    private long version;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public UUID getEstimateId() {
        return estimateId;
    }

    public void setEstimateId(UUID estimateId) {
        this.estimateId = estimateId;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public void setArrivalDeadline(LocalDate arrivalDeadline) {
        this.arrivalDeadline = arrivalDeadline;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
