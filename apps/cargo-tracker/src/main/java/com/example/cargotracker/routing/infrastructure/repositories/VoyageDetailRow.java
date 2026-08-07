package com.example.cargotracker.routing.infrastructure.repositories;

import java.time.Instant;

/** 航海詳細の生の行（区間 1 本ぶん）。表示用への変換は {@link MyBatisVoyageQueryService} が行う。 */
public class VoyageDetailRow {

    private String voyageNumber;
    private String vesselName;
    private String carrierName;
    private String cargoTypes;
    private String departure;
    private String departureName;
    private String arrival;
    private String arrivalName;
    private Instant departureTime;
    private Instant arrivalTime;

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

    public String getCargoTypes() {
        return cargoTypes;
    }

    public void setCargoTypes(String cargoTypes) {
        this.cargoTypes = cargoTypes;
    }

    public String getDeparture() {
        return departure;
    }

    public void setDeparture(String departure) {
        this.departure = departure;
    }

    public String getDepartureName() {
        return departureName;
    }

    public void setDepartureName(String departureName) {
        this.departureName = departureName;
    }

    public String getArrival() {
        return arrival;
    }

    public void setArrival(String arrival) {
        this.arrival = arrival;
    }

    public String getArrivalName() {
        return arrivalName;
    }

    public void setArrivalName(String arrivalName) {
        this.arrivalName = arrivalName;
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
}
