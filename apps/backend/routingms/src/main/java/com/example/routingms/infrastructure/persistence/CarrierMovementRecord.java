package com.example.routingms.infrastructure.persistence;

import java.time.Instant;

/** carrier_movement テーブルの 1 行。 */
public class CarrierMovementRecord {

    private Long id;
    private Long voyageId;
    private String departureLocationUnlocode;
    private String departureLocationName;
    private String arrivalLocationUnlocode;
    private String arrivalLocationName;
    private Instant departureDate;
    private Instant arrivalDate;
    private int seqNumber;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getDepartureLocationName() {
        return departureLocationName;
    }

    public void setDepartureLocationName(String departureLocationName) {
        this.departureLocationName = departureLocationName;
    }

    public String getArrivalLocationUnlocode() {
        return arrivalLocationUnlocode;
    }

    public void setArrivalLocationUnlocode(String arrivalLocationUnlocode) {
        this.arrivalLocationUnlocode = arrivalLocationUnlocode;
    }

    public String getArrivalLocationName() {
        return arrivalLocationName;
    }

    public void setArrivalLocationName(String arrivalLocationName) {
        this.arrivalLocationName = arrivalLocationName;
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
