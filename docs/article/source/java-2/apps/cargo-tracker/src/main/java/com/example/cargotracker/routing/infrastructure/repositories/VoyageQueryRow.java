package com.example.cargotracker.routing.infrastructure.repositories;

import java.time.Instant;

/** 航海検索の生の行。表示用への変換は {@link MyBatisVoyageQueryService} が行う。 */
public class VoyageQueryRow {

    private String voyageNumber;
    private String vesselName;
    private String carrierName;
    private String origin;
    private String originName;
    private String destination;
    private String destinationName;
    private Instant departureTime;
    private Instant arrivalTime;
    private int movementCount;
    private String cargoTypes;

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getVesselName() {
        return vesselName;
    }

    public void setVesselName(String vesselName) {
        this.vesselName = vesselName;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getOriginName() {
        return originName;
    }

    public void setOriginName(String originName) {
        this.originName = originName;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public Instant getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Instant departureTime) {
        this.departureTime = departureTime;
    }

    public Instant getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Instant arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public int getMovementCount() {
        return movementCount;
    }

    public void setMovementCount(int movementCount) {
        this.movementCount = movementCount;
    }

    public String getCargoTypes() {
        return cargoTypes;
    }

    public void setCargoTypes(String cargoTypes) {
        this.cargoTypes = cargoTypes;
    }
}
