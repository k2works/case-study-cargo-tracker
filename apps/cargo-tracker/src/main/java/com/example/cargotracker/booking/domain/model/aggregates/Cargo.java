package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.Description;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.Quantity;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.shared.domain.model.ShipperId;

import java.math.BigDecimal;
import java.util.Objects;

public class Cargo {

    private final BookingId bookingId;
    private final ShipperId shipperId;
    private final CargoType cargoType;
    private final BigDecimal weight;
    private final Dimensions dimensions;
    private final Quantity quantity;
    private final Description description;
    private final RouteSpecification routeSpecification;
    private final BookingStatus status;
    private final HazardousDeclaration hazardousDeclaration;
    private final TemperatureRequirement temperatureRequirement;

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            RouteSpecification routeSpecification
    ) {
        this(bookingId, shipperId, cargoType, weight, null, null, null, routeSpecification, BookingStatus.PRELIMINARY, null, null);
    }

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            RouteSpecification routeSpecification,
            BookingStatus status
    ) {
        this(bookingId, shipperId, cargoType, weight, null, null, null, routeSpecification, status, null, null);
    }

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            Dimensions dimensions,
            Quantity quantity,
            Description description,
            RouteSpecification routeSpecification,
            BookingStatus status
    ) {
        this(bookingId, shipperId, cargoType, weight, dimensions, quantity, description, routeSpecification, status, null, null);
    }

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            Dimensions dimensions,
            Quantity quantity,
            Description description,
            RouteSpecification routeSpecification,
            BookingStatus status,
            HazardousDeclaration hazardousDeclaration,
            TemperatureRequirement temperatureRequirement
    ) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
        this.shipperId = Objects.requireNonNull(shipperId, "shipperId must not be null");
        this.cargoType = Objects.requireNonNull(cargoType, "cargoType must not be null");
        this.weight = Objects.requireNonNull(weight, "weight must not be null");
        this.dimensions = dimensions;
        this.quantity = quantity;
        this.description = description;
        this.routeSpecification = Objects.requireNonNull(routeSpecification, "routeSpecification must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.hazardousDeclaration = hazardousDeclaration;
        this.temperatureRequirement = temperatureRequirement;

        if (this.weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("weight must be greater than zero");
        }
        if (this.cargoType == CargoType.HAZARDOUS && this.hazardousDeclaration == null) {
            throw new IllegalArgumentException("hazardousDeclaration is required for HAZARDOUS cargo");
        }
        if (this.cargoType == CargoType.REFRIGERATED && this.temperatureRequirement == null) {
            throw new IllegalArgumentException("temperatureRequirement is required for REFRIGERATED cargo");
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

    public Dimensions getDimensions() {
        return dimensions;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public Description getDescription() {
        return description;
    }

    public RouteSpecification getRouteSpecification() {
        return routeSpecification;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public HazardousDeclaration getHazardousDeclaration() {
        return hazardousDeclaration;
    }

    public TemperatureRequirement getTemperatureRequirement() {
        return temperatureRequirement;
    }
}
