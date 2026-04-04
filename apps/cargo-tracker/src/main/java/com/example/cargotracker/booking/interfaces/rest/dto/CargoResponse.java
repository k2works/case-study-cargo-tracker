package com.example.cargotracker.booking.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class CargoResponse {

    private final String bookingId;
    private final String shipperId;
    private final String cargoType;
    private final BigDecimal weight;
    private final String originUnlocode;
    private final String destinationUnlocode;
    private final LocalDate arrivalDeadline;
    private final String status;

    public CargoResponse(
            String bookingId,
            String shipperId,
            String cargoType,
            BigDecimal weight,
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String status
    ) {
        this.bookingId = bookingId;
        this.shipperId = shipperId;
        this.cargoType = cargoType;
        this.weight = weight;
        this.originUnlocode = originUnlocode;
        this.destinationUnlocode = destinationUnlocode;
        this.arrivalDeadline = arrivalDeadline;
        this.status = status;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getShipperId() {
        return shipperId;
    }

    public String getCargoType() {
        return cargoType;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public String getOriginUnlocode() {
        return originUnlocode;
    }

    public String getDestinationUnlocode() {
        return destinationUnlocode;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public String getStatus() {
        return status;
    }
}
