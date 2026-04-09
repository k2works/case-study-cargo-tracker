package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureUnit;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        BigDecimal weight = new BigDecimal("10.500");
        assertThrows(NullPointerException.class, () ->
                createCargo(null, SHIPPER_ID, CargoType.GENERAL, weight, ROUTE_SPEC));
    }

    @Test
    void shouldAcceptWeightAtLowerBound_0001() {
        Cargo cargo = assertDoesNotThrow(() -> new Cargo(
                BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("0.001"), ROUTE_SPEC));
        assertEquals(new BigDecimal("0.001"), cargo.getWeight());
    }

    @Test
    void shouldThrowWhenWeightIsExactlyZero() {
        BigDecimal invalidWeight = BigDecimal.ZERO;
        assertThrows(IllegalArgumentException.class, () -> createCargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, invalidWeight, ROUTE_SPEC));
    }

    @Test
    void shouldThrowWhenWeightIsNegative() {
        BigDecimal invalidWeight = new BigDecimal("-0.001");
        assertThrows(IllegalArgumentException.class, () -> createCargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, invalidWeight, ROUTE_SPEC));
    }

    @Test
    void shouldCreateCargoWithOptionalFields() {
        var dimensions = new com.example.cargotracker.booking.domain.model.valueobjects.Dimensions(
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("3"));
        var quantity = new com.example.cargotracker.booking.domain.model.valueobjects.Quantity(5);
        var description = new com.example.cargotracker.booking.domain.model.valueobjects.Description("電子部品");

        Cargo cargo = createCargo(
                CargoType.GENERAL,
                new BigDecimal("10"),
                BookingStatus.PRELIMINARY,
                Cargo.details(dimensions, quantity, description),
                null
        );

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
        Location destination = new Location("USLAX");
        LocalDate arrivalDeadline = LocalDate.now().plusDays(7);
        assertThrows(NullPointerException.class, () ->
                new RouteSpecification(null, destination, arrivalDeadline));
    }

    @Test
    void shouldThrowWhenRouteSpecDestinationIsNull() {
        Location origin = new Location("JPTYO");
        LocalDate arrivalDeadline = LocalDate.now().plusDays(7);
        assertThrows(NullPointerException.class, () ->
                new RouteSpecification(origin, null, arrivalDeadline));
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
        BigDecimal weight = new BigDecimal("10");
        assertThrows(NullPointerException.class, () ->
                createCargo(BOOKING_ID, null, CargoType.GENERAL, weight, ROUTE_SPEC));
    }

    @Test
    void shouldThrowWhenCargoTypeIsNull() {
        BigDecimal weight = new BigDecimal("10");
        assertThrows(NullPointerException.class, () ->
                createCargo(BOOKING_ID, SHIPPER_ID, null, weight, ROUTE_SPEC));
    }

    @Test
    void shouldThrowWhenWeightIsNull() {
        assertThrows(NullPointerException.class, () ->
                createCargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, null, ROUTE_SPEC));
    }

    // --- 危険物貨物テスト ---

    private static final HazardousDeclaration HAZARDOUS_DECLARATION =
            new HazardousDeclaration("3", "UN1203", "Gasoline");

    private static final TemperatureRequirement TEMPERATURE_REQUIREMENT =
            new TemperatureRequirement(new BigDecimal("-25"), new BigDecimal("-18"), TemperatureUnit.CELSIUS);

    @Test
    void shouldCreateHazardousCargo() {
        Cargo cargo = assertDoesNotThrow(() -> createCargo(
                CargoType.HAZARDOUS,
                new BigDecimal("10"),
                BookingStatus.PRELIMINARY,
                null,
                Cargo.handling(HAZARDOUS_DECLARATION, null)
        ));
        assertEquals(CargoType.HAZARDOUS, cargo.getCargoType());
    }

    @Test
    void shouldGetHazardousDeclaration() {
        Cargo cargo = createCargo(
                CargoType.HAZARDOUS,
                new BigDecimal("10"),
                BookingStatus.PRELIMINARY,
                null,
                Cargo.handling(HAZARDOUS_DECLARATION, null)
        );
        assertNotNull(cargo.getHazardousDeclaration());
        assertEquals("3", cargo.getHazardousDeclaration().hazardousClass());
        assertEquals("UN1203", cargo.getHazardousDeclaration().unNumber());
    }

    @Test
    void shouldThrowWhenHazardousCargoMissingDeclaration() {
        BigDecimal weight = new BigDecimal("10");
        assertThrows(IllegalArgumentException.class, () ->
                createCargo(CargoType.HAZARDOUS, weight, BookingStatus.PRELIMINARY, null, null));
    }

    // --- 冷凍・冷蔵貨物テスト ---

    @Test
    void shouldCreateRefrigeratedCargo() {
        Cargo cargo = assertDoesNotThrow(() -> createCargo(
                CargoType.REFRIGERATED,
                new BigDecimal("10"),
                BookingStatus.PRELIMINARY,
                null,
                Cargo.handling(null, TEMPERATURE_REQUIREMENT)
        ));
        assertEquals(CargoType.REFRIGERATED, cargo.getCargoType());
    }

    @Test
    void shouldGetTemperatureRequirement() {
        Cargo cargo = createCargo(
                CargoType.REFRIGERATED,
                new BigDecimal("10"),
                BookingStatus.PRELIMINARY,
                null,
                Cargo.handling(null, TEMPERATURE_REQUIREMENT)
        );
        assertNotNull(cargo.getTemperatureRequirement());
        assertEquals(new BigDecimal("-25"), cargo.getTemperatureRequirement().minTemperature());
        assertEquals(new BigDecimal("-18"), cargo.getTemperatureRequirement().maxTemperature());
    }

    @Test
    void shouldThrowWhenRefrigeratedCargoMissingTemperatureRequirement() {
        BigDecimal weight = new BigDecimal("10");
        assertThrows(IllegalArgumentException.class, () ->
                createCargo(CargoType.REFRIGERATED, weight, BookingStatus.PRELIMINARY, null, null));
    }

    // --- 状態遷移テスト ---

    @Test
    void shouldConfirmCargo_whenStatusIsPreliminary() {
        Cargo cargo = new Cargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("10"), ROUTE_SPEC);
        assertEquals(BookingStatus.PRELIMINARY, cargo.getStatus());

        Cargo confirmed = cargo.confirm();

        assertEquals(BookingStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(BOOKING_ID, confirmed.getBookingId());
    }

    @Test
    void shouldPreserveAllFieldsWhenConfirmed() {
        Cargo cargo = new Cargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("10"), ROUTE_SPEC);

        Cargo confirmed = cargo.confirm();

        assertEquals(BOOKING_ID, confirmed.getBookingId());
        assertEquals(SHIPPER_ID, confirmed.getShipperId());
        assertEquals(CargoType.GENERAL, confirmed.getCargoType());
        assertEquals(new BigDecimal("10"), confirmed.getWeight());
        assertEquals(ROUTE_SPEC, confirmed.getRouteSpecification());
        assertEquals(BookingStatus.CONFIRMED, confirmed.getStatus());
    }

    @Test
    void shouldPreserveAllFieldsWhenCancelled() {
        Cargo cargo = new Cargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("10"), ROUTE_SPEC);

        Cargo cancelled = cargo.cancel();

        assertEquals(BOOKING_ID, cancelled.getBookingId());
        assertEquals(SHIPPER_ID, cancelled.getShipperId());
        assertEquals(CargoType.GENERAL, cancelled.getCargoType());
        assertEquals(new BigDecimal("10"), cancelled.getWeight());
        assertEquals(ROUTE_SPEC, cancelled.getRouteSpecification());
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = "PRELIMINARY", mode = EnumSource.Mode.EXCLUDE)
    void shouldThrowWhenConfirmingNonPreliminaryCargo(BookingStatus status) {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), status, null, null);

        assertThrows(IllegalStateException.class, cargo::confirm);
    }

    @Test
    void shouldCancelCargo_fromPreliminaryStatus() {
        Cargo cargo = new Cargo(BOOKING_ID, SHIPPER_ID, CargoType.GENERAL, new BigDecimal("10"), ROUTE_SPEC);

        Cargo cancelled = cargo.cancel();

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertEquals(BOOKING_ID, cancelled.getBookingId());
    }

    @Test
    void shouldCancelCargo_fromConfirmedStatus() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.CONFIRMED, null, null);

        Cargo cancelled = cargo.cancel();

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void shouldThrowWhenCancellingAlreadyCancelledCargo() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.CANCELLED, null, null);

        assertThrows(IllegalStateException.class, cargo::cancel);
    }

    @Test
    void shouldThrowWhenCancellingSettledCargo() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.SETTLED, null, null);

        assertThrows(IllegalStateException.class, cargo::cancel);
    }

    // --- 一般貨物テスト ---

    @Test
    void shouldCreateGeneralCargoWithoutSpecialFields() {
        Cargo cargo = assertDoesNotThrow(() ->
                createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.PRELIMINARY, null, null));
        assertNull(cargo.getHazardousDeclaration());
        assertNull(cargo.getTemperatureRequirement());
    }

    // --- requireStatus テスト ---

    @Test
    void requireStatus_shouldPassWhenStatusMatches() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.PRELIMINARY, null, null);

        assertDoesNotThrow(() -> cargo.requireStatus(BookingStatus.PRELIMINARY));
    }

    @Test
    void requireStatus_shouldThrowWhenStatusDoesNotMatch() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.CONFIRMED, null, null);

        assertThrows(IllegalStateException.class, () -> cargo.requireStatus(BookingStatus.PRELIMINARY));
    }

    @Test
    void shouldAssignToRoutingFromPreliminaryStatus() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.PRELIMINARY, null, null);

        Cargo routed = cargo.assignToRouting();

        assertEquals(BookingStatus.ROUTE_PROPOSED, routed.getStatus());
        assertEquals(BOOKING_ID, routed.getBookingId());
        assertEquals(SHIPPER_ID, routed.getShipperId());
        assertEquals(new BigDecimal("10"), routed.getWeight());
        assertEquals(ROUTE_SPEC, routed.getRouteSpecification());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = "PRELIMINARY", mode = EnumSource.Mode.EXCLUDE)
    void shouldThrowWhenAssignToRoutingFromNonPreliminaryStatus(BookingStatus status) {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), status, null, null);

        assertThrows(IllegalStateException.class, cargo::assignToRouting);
    }

    // --- 経路情報割り当てテスト ---

    private static CargoItinerary createSampleItinerary() {
        Leg leg = new Leg(
                "V001",
                new Location("JPTYO"),
                new Location("USNYC"),
                LocalDateTime.of(2026, 5, 10, 18, 0),
                LocalDateTime.of(2026, 6, 10, 8, 0)
        );
        return new CargoItinerary(List.of(leg));
    }

    @Test
    void assignItinerary_PRELIMINARY状態から経路情報を割り当てできる() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.PRELIMINARY, null, null);
        CargoItinerary itinerary = createSampleItinerary();

        Cargo result = cargo.assignItinerary(itinerary);

        assertEquals(BookingStatus.ROUTE_PROPOSED, result.getStatus());
        assertEquals(itinerary, result.getCargoItinerary());
    }

    @Test
    void assignItinerary_ROUTE_PROPOSED状態から再割り当てできる() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.ROUTE_PROPOSED, null, null);
        CargoItinerary itinerary = createSampleItinerary();

        Cargo result = cargo.assignItinerary(itinerary);

        assertEquals(BookingStatus.ROUTE_PROPOSED, result.getStatus());
        assertEquals(itinerary, result.getCargoItinerary());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PRELIMINARY", "ROUTE_PROPOSED"}, mode = EnumSource.Mode.EXCLUDE)
    void assignItinerary_他ステータスからは例外が発生する(BookingStatus status) {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), status, null, null);
        CargoItinerary itinerary = createSampleItinerary();

        assertThrows(IllegalStateException.class, () -> cargo.assignItinerary(itinerary));
    }

    @Test
    void assignItinerary_nullを渡すと例外が発生する() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.PRELIMINARY, null, null);

        assertThrows(NullPointerException.class, () -> cargo.assignItinerary(null));
    }

    @Test
    void assignItinerary_元のフィールドが保持される() {
        Cargo cargo = createCargo(CargoType.GENERAL, new BigDecimal("10"), BookingStatus.PRELIMINARY, null, null);
        CargoItinerary itinerary = createSampleItinerary();

        Cargo result = cargo.assignItinerary(itinerary);

        assertEquals(BOOKING_ID, result.getBookingId());
        assertEquals(SHIPPER_ID, result.getShipperId());
        assertEquals(CargoType.GENERAL, result.getCargoType());
        assertEquals(new BigDecimal("10"), result.getWeight());
        assertEquals(ROUTE_SPEC, result.getRouteSpecification());
    }

    private Cargo createCargo(
            BookingId bookingId,
            ShipperId shipperId,
            CargoType cargoType,
            BigDecimal weight,
            RouteSpecification routeSpecification
    ) {
        return new Cargo(bookingId, shipperId, cargoType, weight, routeSpecification);
    }

    private Cargo createCargo(
            CargoType cargoType,
            BigDecimal weight,
            BookingStatus status,
            Cargo.Details details,
            Cargo.Handling handling
    ) {
        return new Cargo(BOOKING_ID, SHIPPER_ID, cargoType, weight, Cargo.state(ROUTE_SPEC, status, details, handling));
    }
}
