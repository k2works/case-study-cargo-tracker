package com.example.cargotracker.estimation.domain.model;

import com.example.cargotracker.shared.domain.model.Location;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Estimate {

    private final EstimateId estimateId;
    private final Location origin;
    private final Location destination;
    private final LocalDate arrivalDeadline;
    private final CargoType cargoType;
    private final BigDecimal weightKg;
    private final List<RouteCandidate> candidates;
    private EstimateStatus status;

    private Estimate(
            EstimateId estimateId,
            Location origin,
            Location destination,
            LocalDate arrivalDeadline,
            CargoType cargoType,
            BigDecimal weightKg
    ) {
        this.estimateId = estimateId;
        this.origin = origin;
        this.destination = destination;
        this.arrivalDeadline = arrivalDeadline;
        this.cargoType = cargoType;
        this.weightKg = weightKg;
        this.candidates = new ArrayList<>();
        this.status = EstimateStatus.CREATED;
    }

    public static Estimate create(
            Location origin,
            Location destination,
            LocalDate arrivalDeadline,
            CargoType cargoType,
            BigDecimal weightKg
    ) {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        if (Objects.equals(origin, destination)) {
            throw new IllegalArgumentException("origin and destination must be different");
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("arrivalDeadline must not be null");
        }
        Objects.requireNonNull(cargoType, "cargoType must not be null");
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("weightKg must be positive");
        }
        return new Estimate(
                EstimateId.generate(),
                origin,
                destination,
                arrivalDeadline,
                cargoType,
                weightKg
        );
    }

    @SuppressWarnings("java:S107")
    public static Estimate reconstruct(
            EstimateId estimateId,
            Location origin,
            Location destination,
            LocalDate arrivalDeadline,
            CargoType cargoType,
            BigDecimal weightKg,
            EstimateStatus status,
            List<RouteCandidate> candidates
    ) {
        Estimate estimate = new Estimate(
                estimateId, origin, destination,
                arrivalDeadline, cargoType, weightKg
        );
        estimate.status = status;
        estimate.candidates.addAll(candidates);
        return estimate;
    }

    public void replaceCandidates(List<RouteCandidate> newCandidates) {
        this.candidates.clear();
        this.candidates.addAll(newCandidates);
    }

    public EstimateId getEstimateId() {
        return estimateId;
    }

    public Location getOrigin() {
        return origin;
    }

    public Location getDestination() {
        return destination;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public CargoType getCargoType() {
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
