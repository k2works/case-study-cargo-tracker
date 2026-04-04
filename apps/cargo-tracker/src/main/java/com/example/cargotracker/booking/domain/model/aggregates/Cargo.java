package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;

import java.math.BigDecimal;
import java.util.Objects;

public class Cargo {

    private final BookingId bookingId;
    private final ShipperId shipperId;
    private final CargoType cargoType;
    private final BigDecimal weight;
    private final RouteSpecification routeSpecification;
    private final BookingStatus status;

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            RouteSpecification routeSpecification
    ) {
        this(
                bookingId,
                shipperId,
                cargoType,
                weight,
                routeSpecification,
                BookingStatus.PRELIMINARY
        );
    }

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            RouteSpecification routeSpecification,
            BookingStatus status
    ) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
        this.shipperId = Objects.requireNonNull(shipperId, "shipperId must not be null");
        this.cargoType = Objects.requireNonNull(cargoType, "cargoType must not be null");
        this.weight = Objects.requireNonNull(weight, "weight must not be null");
        this.routeSpecification = Objects.requireNonNull(routeSpecification, "routeSpecification must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");

        if (this.weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("weight must be greater than zero");
        }
    }

    public BookingId getBookingId() {
        return bookingId;
    }

    public ShipperId getShipperId() {
        return shipperId;
    }

    public CargoType getCargoType() {
        return cargoType;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public RouteSpecification getRouteSpecification() {
        return routeSpecification;
    }

    public BookingStatus getStatus() {
        return status;
    }
}
