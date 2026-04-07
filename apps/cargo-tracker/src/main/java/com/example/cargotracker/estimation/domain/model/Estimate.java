package com.example.cargotracker.estimation.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Estimate {

    private final EstimateId estimateId;
    private final String originUnlocode;
    private final String destinationUnlocode;
    private final LocalDate arrivalDeadline;
    private final String cargoType;
    private final BigDecimal weightKg;
    private final List<RouteCandidate> candidates;
    private EstimateStatus status;

    private Estimate(
            EstimateId estimateId,
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg
    ) {
        this.estimateId = estimateId;
        this.originUnlocode = originUnlocode;
        this.destinationUnlocode = destinationUnlocode;
        this.arrivalDeadline = arrivalDeadline;
        this.cargoType = cargoType;
        this.weightKg = weightKg;
        this.candidates = new ArrayList<>();
        this.status = EstimateStatus.CREATED;
    }

    public static Estimate create(
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg
    ) {
        if (originUnlocode == null || originUnlocode.isBlank()) {
            throw new IllegalArgumentException("originUnlocode must not be blank");
        }
        if (destinationUnlocode == null || destinationUnlocode.isBlank()) {
            throw new IllegalArgumentException("destinationUnlocode must not be blank");
        }
        if (Objects.equals(originUnlocode, destinationUnlocode)) {
            throw new IllegalArgumentException("origin and destination must be different");
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("arrivalDeadline must not be null");
        }
        if (cargoType == null || cargoType.isBlank()) {
            throw new IllegalArgumentException("cargoType must not be blank");
        }
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("weightKg must be positive");
        }
        return new Estimate(
                EstimateId.generate(),
                originUnlocode,
                destinationUnlocode,
                arrivalDeadline,
                cargoType,
                weightKg
        );
    }

    public static Estimate reconstruct(
            EstimateId estimateId,
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg,
            EstimateStatus status,
            List<RouteCandidate> candidates
    ) {
        Estimate estimate = new Estimate(
                estimateId, originUnlocode, destinationUnlocode,
                arrivalDeadline, cargoType, weightKg
        );
        estimate.status = status;
        estimate.candidates.addAll(candidates);
        return estimate;
    }

    public void addCandidates(List<RouteCandidate> newCandidates) {
        this.candidates.clear();
        this.candidates.addAll(newCandidates);
    }

    public EstimateId getEstimateId() {
        return estimateId;
    }

    public String getOriginUnlocode() {
        return originUnlocode;
    }

    public String getDestinationUnlocode() {
        return destinationUnlocode;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public String getCargoType() {
        return cargoType;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public List<RouteCandidate> getCandidates() {
        return List.copyOf(candidates);
    }

    public EstimateStatus getStatus() {
        return status;
    }
}
