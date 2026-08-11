package com.example.cargotracker.estimation.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 読み取りクエリの生の行。表示用への変換は {@link MyBatisEstimateQueryService} が行う。
 *
 * <p><strong>平坦なのは SQL の結果がそうだからである</strong>（ADR-022 と同じ形）。
 * MyBatis は 1 行の結果集合を入れ子のレコードへ直接は組み立てられない。
 */
public class EstimateQueryRow {

    private String estimateId;
    private String origin;
    private String destination;
    private String cargoType;
    private BigDecimal weightKg;
    private LocalDate arrivalDeadline;
    private String status;
    private Instant createdAt;
    private BigDecimal cheapestCost;

    public String getEstimateId() {
        return estimateId;
    }

    public void setEstimateId(String estimateId) {
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

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public void setArrivalDeadline(LocalDate arrivalDeadline) {
        this.arrivalDeadline = arrivalDeadline;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getCheapestCost() {
        return cheapestCost;
    }

    public void setCheapestCost(BigDecimal cheapestCost) {
        this.cheapestCost = cheapestCost;
    }
}
