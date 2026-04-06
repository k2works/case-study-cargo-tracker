package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureUnit;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CargoTest {

    private static final BookingId BOOKING_ID = new BookingId(UUID.randomUUID());
    private static final ShipperId SHIPPER_ID = new ShipperId(UUID.randomUUID());
    private static final RouteSpecification ROUTE_SPEC = new RouteSpecification(
            new Location("JPTYO"), new Location("USLAX"), LocalDate.now().plusDays(7));


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
        assertThrows(NullPointerException.class, () -> new Cargo(
                null, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("10.500"), ROUTE_SPEC));
    }

    @Test
    void shouldAcceptWeightAtLowerBound_0001() {
        Cargo cargo = assertDoesNotThrow(() -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("0.001"), ROUTE_SPEC));
        assertEquals(new BigDecimal("0.001"), cargo.getWeight());
    }

    @Test
    void shouldThrowWhenWeightIsExactlyZero() {
        assertThrows(IllegalArgumentException.class, () -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, BigDecimal.ZERO, ROUTE_SPEC));
    }

    @Test
    void shouldThrowWhenWeightIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("-0.001"), ROUTE_SPEC));
    }

    @Test
    void shouldCreateCargoWithOptionalFields() {
        var dimensions = new com.example.cargotracker.booking.domain.model.valueobjects.Dimensions(
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("3"));
        var quantity = new com.example.cargotracker.booking.domain.model.valueobjects.Quantity(5);
        var description = new com.example.cargotracker.booking.domain.model.valueobjects.Description("電子部品");

        Cargo cargo = new Cargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("10"),
                dimensions, quantity, description, ROUTE_SPEC, BookingStatus.PRELIMINARY);

        assertEquals(dimensions, cargo.getDimensions());
        assertEquals(quantity, cargo.getQuantity());
        assertEquals(description, cargo.getDescription());
    }

    @Test
    void shouldCreateCargoWithoutOptionalFields() {
        Cargo cargo = new Cargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("10"), ROUTE_SPEC);

        assertNull(cargo.getDimensions());
        assertNull(cargo.getQuantity());
        assertNull(cargo.getDescription());
    }

    @Test
    void shouldAcceptArrivalDeadlineToday() {
        RouteSpecification spec = assertDoesNotThrow(() ->
                new RouteSpecification(new Location("JPTYO"), new Location("USLAX"), LocalDate.now()));
        assertEquals(LocalDate.now(), spec.arrivalDeadline());
    }

    @Test
    void shouldThrowWhenRouteSpecOriginIsNull() {
        assertThrows(NullPointerException.class, () ->
                new RouteSpecification(null, new Location("USLAX"), LocalDate.now().plusDays(7)));
    }

    @Test
    void shouldThrowWhenRouteSpecDestinationIsNull() {
        assertThrows(NullPointerException.class, () ->
                new RouteSpecification(new Location("JPTYO"), null, LocalDate.now().plusDays(7)));
    }

    @Test
    void shouldThrowWhenLocationUnlocodeIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new Location(""));
        assertThrows(IllegalArgumentException.class, () -> new Location(null));
    }

    @Test
    void shouldThrowWhenLocationUnlocodePatternInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new Location("abc"));
        assertThrows(IllegalArgumentException.class, () -> new Location("JPTYO1"));
    }

    @Test
    void shouldThrowWhenShipperIdIsNull() {
        assertThrows(NullPointerException.class, () -> new Cargo(
                BOOKING_ID, null, CargoType.GENERAL, new BigDecimal("10"), ROUTE_SPEC));
    }

    @Test
    void shouldThrowWhenCargoTypeIsNull() {
        assertThrows(NullPointerException.class, () -> new Cargo(
                BOOKING_ID, SHIPPER_ID, null, new BigDecimal("10"), ROUTE_SPEC));
    }

    @Test
    void shouldThrowWhenWeightIsNull() {
        assertThrows(NullPointerException.class, () -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, null, ROUTE_SPEC));
    }

    // --- 危険物貨物テスト ---

    private static final HazardousDeclaration HAZARDOUS_DECLARATION =
            new HazardousDeclaration("3", "UN1203", "Gasoline");

    private static final TemperatureRequirement TEMPERATURE_REQUIREMENT =
            new TemperatureRequirement(new BigDecimal("-25"), new BigDecimal("-18"), TemperatureUnit.CELSIUS);

    @Test
    void shouldCreateHazardousCargo() {
        Cargo cargo = assertDoesNotThrow(() -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.HAZARDOUS, new BigDecimal("10"),
                null, null, null, ROUTE_SPEC, BookingStatus.PRELIMINARY,
                HAZARDOUS_DECLARATION, null));
        assertEquals(CargoType.HAZARDOUS, cargo.getCargoType());
    }

    @Test
    void shouldGetHazardousDeclaration() {
        Cargo cargo = new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.HAZARDOUS, new BigDecimal("10"),
                null, null, null, ROUTE_SPEC, BookingStatus.PRELIMINARY,
                HAZARDOUS_DECLARATION, null);
        assertNotNull(cargo.getHazardousDeclaration());
        assertEquals("3", cargo.getHazardousDeclaration().hazardousClass());
        assertEquals("UN1203", cargo.getHazardousDeclaration().unNumber());
    }

    @Test
    void shouldThrowWhenHazardousCargoMissingDeclaration() {
        assertThrows(IllegalArgumentException.class, () -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.HAZARDOUS, new BigDecimal("10"),
                null, null, null, ROUTE_SPEC, BookingStatus.PRELIMINARY,
                null, null));
    }

    // --- 冷凍・冷蔵貨物テスト ---

    @Test
    void shouldCreateRefrigeratedCargo() {
        Cargo cargo = assertDoesNotThrow(() -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.REFRIGERATED, new BigDecimal("10"),
                null, null, null, ROUTE_SPEC, BookingStatus.PRELIMINARY,
                null, TEMPERATURE_REQUIREMENT));
        assertEquals(CargoType.REFRIGERATED, cargo.getCargoType());
    }

    @Test
    void shouldGetTemperatureRequirement() {
        Cargo cargo = new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.REFRIGERATED, new BigDecimal("10"),
                null, null, null, ROUTE_SPEC, BookingStatus.PRELIMINARY,
                null, TEMPERATURE_REQUIREMENT);
        assertNotNull(cargo.getTemperatureRequirement());
        assertEquals(new BigDecimal("-25"), cargo.getTemperatureRequirement().minTemperature());
        assertEquals(new BigDecimal("-18"), cargo.getTemperatureRequirement().maxTemperature());
    }

    @Test
    void shouldThrowWhenRefrigeratedCargoMissingTemperatureRequirement() {
        assertThrows(IllegalArgumentException.class, () -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.REFRIGERATED, new BigDecimal("10"),
                null, null, null, ROUTE_SPEC, BookingStatus.PRELIMINARY,
                null, null));
    }

    // --- 一般貨物テスト ---

    @Test
    void shouldCreateGeneralCargoWithoutSpecialFields() {
        Cargo cargo = assertDoesNotThrow(() -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("10"),
                null, null, null, ROUTE_SPEC, BookingStatus.PRELIMINARY,
                null, null));
        assertNull(cargo.getHazardousDeclaration());
        assertNull(cargo.getTemperatureRequirement());
    }
}
