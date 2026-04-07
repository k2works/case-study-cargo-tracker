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
import java.util.EnumSet;
import java.util.Objects;

public class Cargo {

    private static final EnumSet<BookingStatus> CANCELLABLE_STATUSES =
            EnumSet.of(BookingStatus.PRELIMINARY, BookingStatus.ROUTE_PROPOSED, BookingStatus.CONFIRMED,
                    BookingStatus.TRACKING_ISSUED, BookingStatus.IN_TRANSIT);

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

    public record Details(
            Dimensions dimensions,
            Quantity quantity,
            Description description
    ) {
    }

    public record Handling(
            HazardousDeclaration hazardousDeclaration,
            TemperatureRequirement temperatureRequirement
    ) {
    }

    public record State(
            RouteSpecification routeSpecification,
            BookingStatus status,
            Details details,
            Handling handling
    ) {
    }

    public static Details details(Dimensions dimensions, Quantity quantity, Description description) {
        return new Details(dimensions, quantity, description);
    }

    public static Handling handling(
            HazardousDeclaration hazardousDeclaration,
            TemperatureRequirement temperatureRequirement
    ) {
        return new Handling(hazardousDeclaration, temperatureRequirement);
    }

    public static State state(
            RouteSpecification routeSpecification,
            BookingStatus status,
            Details details,
            Handling handling
    ) {
        return new State(routeSpecification, status, details, handling);
    }

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            RouteSpecification routeSpecification
    ) {
        this(bookingId, shipperId, cargoType, weight, state(routeSpecification, BookingStatus.PRELIMINARY, null, null));
    }

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            RouteSpecification routeSpecification,
            BookingStatus status
    ) {
        this(bookingId, shipperId, cargoType, weight, state(routeSpecification, status, null, null));
    }

    public Cargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            State state
    ) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
        this.shipperId = Objects.requireNonNull(shipperId, "shipperId must not be null");
        this.cargoType = Objects.requireNonNull(cargoType, "cargoType must not be null");
        this.weight = Objects.requireNonNull(weight, "weight must not be null");
        State cargoState = Objects.requireNonNull(state, "state must not be null");
        this.dimensions = cargoState.details() != null ? cargoState.details().dimensions() : null;
        this.quantity = cargoState.details() != null ? cargoState.details().quantity() : null;
        this.description = cargoState.details() != null ? cargoState.details().description() : null;
        this.routeSpecification = Objects.requireNonNull(cargoState.routeSpecification(), "routeSpecification must not be null");
        this.status = Objects.requireNonNull(cargoState.status(), "status must not be null");
        this.hazardousDeclaration = cargoState.handling() != null ? cargoState.handling().hazardousDeclaration() : null;
        this.temperatureRequirement = cargoState.handling() != null ? cargoState.handling().temperatureRequirement() : null;

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

    public Cargo confirm() {
        if (status != BookingStatus.PRELIMINARY) {
            throw new IllegalStateException("予約確定は仮受付状態からのみ可能です。現在の状態: " + status.getDisplayName());
        }
        return new Cargo(bookingId, shipperId, cargoType, weight, currentStateWith(BookingStatus.CONFIRMED));
    }

    public Cargo cancel() {
        if (!CANCELLABLE_STATUSES.contains(status)) {
            throw new IllegalStateException("現在の状態ではキャンセルできません。現在の状態: " + status.getDisplayName());
        }
        return new Cargo(bookingId, shipperId, cargoType, weight, currentStateWith(BookingStatus.CANCELLED));
    }

    public Cargo assignToRouting() {
        if (status != BookingStatus.PRELIMINARY) {
            throw new IllegalStateException("経路設計依頼は仮受付状態からのみ可能です。現在の状態: " + status.getDisplayName());
        }
        return new Cargo(bookingId, shipperId, cargoType, weight, currentStateWith(BookingStatus.ROUTE_PROPOSED));
    }

    private State currentStateWith(BookingStatus nextStatus) {
        return state(
                routeSpecification,
                nextStatus,
                details(dimensions, quantity, description),
                handling(hazardousDeclaration, temperatureRequirement)
        );
    }
}
