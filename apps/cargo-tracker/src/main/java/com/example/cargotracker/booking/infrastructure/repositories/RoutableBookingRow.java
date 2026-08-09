package com.example.cargotracker.booking.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 経路割り当ての対象になる予約の行（ACL アダプタが読む）。 */
public class RoutableBookingRow {

    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDate arrivalDeadline;
    private String cargoType;
    private BigDecimal weight;
    private String shipperName;

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

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String getShipperName() {
        return shipperName;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }

    /** 誤配のときの貨物の現在地（最後の荷役の場所）。誤配でなければ null。 */
    private String misroutedFrom;

    public String getMisroutedFrom() {
        return misroutedFrom;
    }

    public void setMisroutedFrom(String misroutedFrom) {
        this.misroutedFrom = misroutedFrom;
    }
}
