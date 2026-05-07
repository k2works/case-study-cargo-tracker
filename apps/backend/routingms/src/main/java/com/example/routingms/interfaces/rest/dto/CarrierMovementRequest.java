package com.example.routingms.interfaces.rest.dto;

import java.time.ZonedDateTime;

/**
 * キャリア移動（運航区間）のリクエスト DTO
 */
public class CarrierMovementRequest {

    private String departureLocationUnlocode;
    private String arrivalLocationUnlocode;
    private ZonedDateTime departureDate;
    private ZonedDateTime arrivalDate;
    private int seqNumber;

    public CarrierMovementRequest() {}

    public String getDepartureLocationUnlocode() { return departureLocationUnlocode; }
    public void setDepartureLocationUnlocode(String departureLocationUnlocode) {
        this.departureLocationUnlocode = departureLocationUnlocode;
    }

    public String getArrivalLocationUnlocode() { return arrivalLocationUnlocode; }
    public void setArrivalLocationUnlocode(String arrivalLocationUnlocode) {
        this.arrivalLocationUnlocode = arrivalLocationUnlocode;
    }

    public ZonedDateTime getDepartureDate() { return departureDate; }
    public void setDepartureDate(ZonedDateTime departureDate) { this.departureDate = departureDate; }

    public ZonedDateTime getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(ZonedDateTime arrivalDate) { this.arrivalDate = arrivalDate; }

    public int getSeqNumber() { return seqNumber; }
    public void setSeqNumber(int seqNumber) { this.seqNumber = seqNumber; }
}
