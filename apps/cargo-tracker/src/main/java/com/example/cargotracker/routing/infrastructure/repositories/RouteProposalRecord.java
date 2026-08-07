package com.example.cargotracker.routing.infrastructure.repositories;

import java.time.LocalDate;
import java.util.UUID;

/** 経路提案の行。 */
public class RouteProposalRecord {

    private Long id;
    private UUID bookingId;
    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDate arrivalDeadline;
    private LocalDate originalArrivalDeadline;
    private String cargoType;
    private java.math.BigDecimal weight;
    private int maxTransitCount;
    private int calculationCount;
    private int candidateCount;
    private String selectedVoyageNumber;
    private long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getOriginUnlocode() {
        return originUnlocode;
    }

    public void setOriginUnlocode(String originUnlocode) {
        this.originUnlocode = originUnlocode;
    }

    public String getDestinationUnlocode() {
        return destinationUnlocode;
    }

    public void setDestinationUnlocode(String destinationUnlocode) {
        this.destinationUnlocode = destinationUnlocode;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public void setArrivalDeadline(LocalDate arrivalDeadline) {
        this.arrivalDeadline = arrivalDeadline;
    }

    public LocalDate getOriginalArrivalDeadline() {
        return originalArrivalDeadline;
    }

    public void setOriginalArrivalDeadline(LocalDate originalArrivalDeadline) {
        this.originalArrivalDeadline = originalArrivalDeadline;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public java.math.BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(java.math.BigDecimal weight) {
        this.weight = weight;
    }

    public int getMaxTransitCount() {
        return maxTransitCount;
    }

    public void setMaxTransitCount(int maxTransitCount) {
        this.maxTransitCount = maxTransitCount;
    }

    public int getCalculationCount() {
        return calculationCount;
    }

    public void setCalculationCount(int calculationCount) {
        this.calculationCount = calculationCount;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(int candidateCount) {
        this.candidateCount = candidateCount;
    }

    public String getSelectedVoyageNumber() {
        return selectedVoyageNumber;
    }

    public void setSelectedVoyageNumber(String selectedVoyageNumber) {
        this.selectedVoyageNumber = selectedVoyageNumber;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
