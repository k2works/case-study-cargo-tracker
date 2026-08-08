package com.example.cargotracker.routing.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.Instant;

/** 経路候補の行。 */
public class ProposedRouteRecord {

    private Long proposalId;
    private String voyageNumber;
    private String transitPorts;
    private int boardingIndex;
    private int landingIndex;
    private Instant departureDate;
    private Instant arrivalDate;
    private int transitDays;
    private BigDecimal estimatedCostValue;
    private String estimatedCostCurrency;
    private boolean capacityAvailable;
    private boolean hazardousAllowed;
    private boolean refrigeratedAllowed;
    private boolean deadlineSatisfied;
    private int priority;

    public Long getProposalId() {
        return proposalId;
    }

    public void setProposalId(Long proposalId) {
        this.proposalId = proposalId;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getTransitPorts() {
        return transitPorts;
    }

    public void setTransitPorts(String transitPorts) {
        this.transitPorts = transitPorts;
    }

    public int getBoardingIndex() {
        return boardingIndex;
    }

    public void setBoardingIndex(int boardingIndex) {
        this.boardingIndex = boardingIndex;
    }

    public int getLandingIndex() {
        return landingIndex;
    }

    public void setLandingIndex(int landingIndex) {
        this.landingIndex = landingIndex;
    }

    public Instant getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(Instant departureDate) {
        this.departureDate = departureDate;
    }

    public Instant getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(Instant arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public int getTransitDays() {
        return transitDays;
    }

    public void setTransitDays(int transitDays) {
        this.transitDays = transitDays;
    }

    public BigDecimal getEstimatedCostValue() {
        return estimatedCostValue;
    }

    public void setEstimatedCostValue(BigDecimal estimatedCostValue) {
        this.estimatedCostValue = estimatedCostValue;
    }

    public String getEstimatedCostCurrency() {
        return estimatedCostCurrency;
    }

    public void setEstimatedCostCurrency(String estimatedCostCurrency) {
        this.estimatedCostCurrency = estimatedCostCurrency;
    }

    public boolean isCapacityAvailable() {
        return capacityAvailable;
    }

    public void setCapacityAvailable(boolean capacityAvailable) {
        this.capacityAvailable = capacityAvailable;
    }

    public boolean isHazardousAllowed() {
        return hazardousAllowed;
    }

    public void setHazardousAllowed(boolean hazardousAllowed) {
        this.hazardousAllowed = hazardousAllowed;
    }

    public boolean isRefrigeratedAllowed() {
        return refrigeratedAllowed;
    }

    public void setRefrigeratedAllowed(boolean refrigeratedAllowed) {
        this.refrigeratedAllowed = refrigeratedAllowed;
    }

    public boolean isDeadlineSatisfied() {
        return deadlineSatisfied;
    }

    public void setDeadlineSatisfied(boolean deadlineSatisfied) {
        this.deadlineSatisfied = deadlineSatisfied;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}
