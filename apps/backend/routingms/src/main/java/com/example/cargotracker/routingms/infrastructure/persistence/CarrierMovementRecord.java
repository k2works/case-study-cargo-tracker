package com.example.cargotracker.routingms.infrastructure.persistence;

import java.time.LocalDateTime;

/** routing_read_db.carrier_movement の 1 行を表す POJO。 */
public class CarrierMovementRecord {

    private String voyageNumber;
    private Integer movementSeq;
    private String departureUnlocode;
    private String arrivalUnlocode;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public Integer getMovementSeq() {
        return movementSeq;
    }

    public void setMovementSeq(Integer movementSeq) {
        this.movementSeq = movementSeq;
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
}
