package com.example.cargotracker.routingms.infrastructure.persistence;

import java.time.LocalDateTime;

/** carrier_movement × voyage_accepted_cargo_type JOIN の 1 行。 */
public class EdgeRecord {

    private String voyageNumber;
    private String departureUnlocode;
    private String arrivalUnlocode;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String cargoType;

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getDepartureUnlocode() {
        return departureUnlocode;
    }

    public void setDepartureUnlocode(String departureUnlocode) {
        this.departureUnlocode = departureUnlocode;
    }

    public String getArrivalUnlocode() {
        return arrivalUnlocode;
    }

    public void setArrivalUnlocode(String arrivalUnlocode) {
        this.arrivalUnlocode = arrivalUnlocode;
    }

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalDateTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public String edgeKey() {
        return voyageNumber + "|" + departureUnlocode + "|" + arrivalUnlocode
                + "|" + departureTime + "|" + arrivalTime;
    }
}
