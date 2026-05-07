package com.example.routingms.domain.model.entities;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * キャリア移動（運航区間）エンティティ
 * 出発地から到着地への1区間の運航情報を表す
 */
public class CarrierMovement {

    private final String departureLocationUnlocode;
    private final String arrivalLocationUnlocode;
    private final ZonedDateTime departureDate;
    private final ZonedDateTime arrivalDate;
    private final int seqNumber;

    public CarrierMovement(
            String departureLocationUnlocode,
            String arrivalLocationUnlocode,
            ZonedDateTime departureDate,
            ZonedDateTime arrivalDate,
            int seqNumber) {

        if (departureLocationUnlocode == null || departureLocationUnlocode.isBlank()) {
            throw new IllegalArgumentException("departureLocationUnlocode must not be blank");
        }
        if (arrivalLocationUnlocode == null || arrivalLocationUnlocode.isBlank()) {
            throw new IllegalArgumentException("arrivalLocationUnlocode must not be blank");
        }
        if (departureDate == null) {
            throw new IllegalArgumentException("departureDate must not be null");
        }
        if (arrivalDate == null) {
            throw new IllegalArgumentException("arrivalDate must not be null");
        }
        if (!arrivalDate.isAfter(departureDate)) {
            throw new IllegalArgumentException("arrivalDate must be after departureDate");
        }
        if (seqNumber < 0) {
            throw new IllegalArgumentException("seqNumber must be non-negative");
        }

        this.departureLocationUnlocode = departureLocationUnlocode;
        this.arrivalLocationUnlocode = arrivalLocationUnlocode;
        this.departureDate = departureDate;
        this.arrivalDate = arrivalDate;
        this.seqNumber = seqNumber;
    }

    public String getDepartureLocationUnlocode() {
        return departureLocationUnlocode;
    }

    public String getArrivalLocationUnlocode() {
        return arrivalLocationUnlocode;
    }

    public ZonedDateTime getDepartureDate() {
        return departureDate;
    }

    public ZonedDateTime getArrivalDate() {
        return arrivalDate;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CarrierMovement that = (CarrierMovement) o;
        return seqNumber == that.seqNumber
                && Objects.equals(departureLocationUnlocode, that.departureLocationUnlocode)
                && Objects.equals(arrivalLocationUnlocode, that.arrivalLocationUnlocode)
                && Objects.equals(departureDate, that.departureDate)
                && Objects.equals(arrivalDate, that.arrivalDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(departureLocationUnlocode, arrivalLocationUnlocode, departureDate, arrivalDate, seqNumber);
    }

    @Override
    public String toString() {
        return "CarrierMovement{" +
                "departureLocationUnlocode='" + departureLocationUnlocode + '\'' +
                ", arrivalLocationUnlocode='" + arrivalLocationUnlocode + '\'' +
                ", departureDate=" + departureDate +
                ", arrivalDate=" + arrivalDate +
                ", seqNumber=" + seqNumber +
                '}';
    }
}
