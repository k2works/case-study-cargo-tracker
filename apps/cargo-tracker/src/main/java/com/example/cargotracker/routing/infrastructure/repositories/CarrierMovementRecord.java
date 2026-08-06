package com.example.cargotracker.routing.infrastructure.repositories;

import java.time.Instant;

/** {@code carrier_movement} テーブルの行。 */
public class CarrierMovementRecord {

    private Long voyageId;
    private String departureLocationUnlocode;
    private String arrivalLocationUnlocode;
    private Instant departureDate;
    private Instant arrivalDate;
    private int seqNumber;

    public Long getVoyageId() {
        return voyageId;
    }

    public void setVoyageId(Long voyageId) {
        this.voyageId = voyageId;
    }

    public String getDepartureLocationUnlocode() {
        return departureLocationUnlocode;
    }

    public void setDepartureLocationUnlocode(String departureLocationUnlocode) {
        this.departureLocationUnlocode = departureLocationUnlocode;
    }

    public String getArrivalLocationUnlocode() {
        return arrivalLocationUnlocode;
    }

    public void setArrivalLocationUnlocode(String arrivalLocationUnlocode) {
        this.arrivalLocationUnlocode = arrivalLocationUnlocode;
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

    public int getSeqNumber() {
        return seqNumber;
    }

    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
    }
}
