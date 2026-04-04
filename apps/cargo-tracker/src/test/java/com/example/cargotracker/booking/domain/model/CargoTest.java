package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CargoTest {

    @Test
    void shouldCreateCargoWithValidData() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        RouteSpecification routeSpecification = new RouteSpecification(
                new Location("JPTYO"),
                new Location("USLAX"),
                LocalDate.now().plusDays(7)
        );

        Cargo cargo = assertDoesNotThrow(() -> new Cargo(
                bookingId,
                shipperId,
                CargoType.GENERAL,
                new BigDecimal("10.500"),
                routeSpecification
        ));

        assertEquals(bookingId, cargo.getBookingId());
        assertEquals(shipperId, cargo.getShipperId());
        assertEquals(CargoType.GENERAL, cargo.getCargoType());
        assertEquals(new BigDecimal("10.500"), cargo.getWeight());
        assertEquals(routeSpecification, cargo.getRouteSpecification());
        assertEquals(BookingStatus.PRELIMINARY, cargo.getStatus());
    }

    @Test
    void shouldThrowWhenWeightIsZeroOrNegative() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        RouteSpecification routeSpecification = new RouteSpecification(
                new Location("JPTYO"),
                new Location("USLAX"),
                LocalDate.now().plusDays(7)
        );

        assertThrows(IllegalArgumentException.class, () -> new Cargo(
                bookingId,
                shipperId,
                CargoType.GENERAL,
                BigDecimal.ZERO,
                routeSpecification
        ));
    }

    @Test
    void shouldThrowWhenOriginAndDestinationAreSame() {
        Location sameOrigin = new Location("JPTYO");
        Location sameDestination = new Location("JPTYO");
        LocalDate arrivalDeadline = LocalDate.now().plusDays(7);

        assertThrows(IllegalArgumentException.class, () ->
                new RouteSpecification(sameOrigin, sameDestination, arrivalDeadline));
    }

    @Test
    void shouldThrowWhenArrivalDeadlineIsPast() {
        Location origin = new Location("JPTYO");
        Location destination = new Location("USLAX");
        LocalDate pastArrivalDeadline = LocalDate.now().minusDays(1);

        assertThrows(IllegalArgumentException.class, () ->
                new RouteSpecification(origin, destination, pastArrivalDeadline));
    }

    @Test
    void shouldThrowWhenBookingIdIsNull() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        RouteSpecification routeSpecification = new RouteSpecification(
                new Location("JPTYO"),
                new Location("USLAX"),
                LocalDate.now().plusDays(7)
        );
        BigDecimal weight = new BigDecimal("10.500");

        assertThrows(NullPointerException.class, () -> new Cargo(
                null,
                shipperId,
                CargoType.GENERAL,
                weight,
                routeSpecification
        ));
    }
}
