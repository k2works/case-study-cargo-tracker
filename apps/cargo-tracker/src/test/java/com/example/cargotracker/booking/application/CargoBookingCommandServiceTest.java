package com.example.cargotracker.booking.application;

import com.example.cargotracker.booking.application.internal.commandservices.AssignToRoutingCommand;
import com.example.cargotracker.booking.domain.model.events.BookingAssignedToRoutingEvent;
import com.example.cargotracker.booking.domain.model.events.BookingCancelledEvent;
import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CancelBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CargoBookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistenceChecker;
import com.example.cargotracker.booking.domain.model.aggregates.BookingStatus;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.aggregates.CargoType;
import com.example.cargotracker.booking.domain.model.exceptions.BookingNotFoundException;
import com.example.cargotracker.booking.domain.model.exceptions.ShipperNotFoundException;
import com.example.cargotracker.booking.domain.model.repository.CargoRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureUnit;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargoBookingCommandServiceTest {

    @Mock
    private CargoRepository cargoRepository;

    @Mock
    private ShipperExistenceChecker shipperExistenceChecker;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CargoBookingCommandService cargoBookingCommandService;

    @Test
    void 有効なコマンドでCargoが保存されBookingIdが返る() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        BookCargoCommand command = new BookCargoCommand(
                shipperId.toString(),
                "GENERAL",
                new BigDecimal("10.500"),
                null, null, null, null, null,
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10),
                null, null, null, null, null, null
        );
        when(shipperExistenceChecker.exists(shipperId)).thenReturn(true);
        ArgumentCaptor<Cargo> cargoCaptor = ArgumentCaptor.forClass(Cargo.class);

        BookingId bookingId = cargoBookingCommandService.bookCargo(command);

        assertNotNull(bookingId);
        verify(cargoRepository).save(cargoCaptor.capture());
        Cargo savedCargo = cargoCaptor.getValue();
        assertEquals(bookingId, savedCargo.getBookingId());
        assertEquals(shipperId, savedCargo.getShipperId());
        assertEquals(command.weight(), savedCargo.getWeight());
        assertEquals(command.arrivalDeadline(), savedCargo.getRouteSpecification().arrivalDeadline());
        assertEquals(command.originUnlocode(), savedCargo.getRouteSpecification().origin().unlocode());
        assertEquals(command.destinationUnlocode(), savedCargo.getRouteSpecification().destination().unlocode());
    }

    @Test
    void 存在しないShipperIdでIllegalArgumentExceptionが発生する() {
        String shipperId = UUID.randomUUID().toString();
        BookCargoCommand command = new BookCargoCommand(
                shipperId,
                "GENERAL",
                new BigDecimal("10.500"),
                null, null, null, null, null,
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10),
                null, null, null, null, null, null
        );
        when(shipperExistenceChecker.exists(new ShipperId(UUID.fromString(shipperId)))).thenReturn(false);

        ShipperNotFoundException exception = assertThrows(
                ShipperNotFoundException.class,
                () -> cargoBookingCommandService.bookCargo(command)
        );

        assertEquals(new ShipperId(UUID.fromString(shipperId)), exception.getShipperId());
        verify(cargoRepository, never()).save(any(Cargo.class));
    }

    @Test
    void shouldCreateHazardousCargoFromCommand() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        BookCargoCommand command = new BookCargoCommand(
                shipperId.toString(),
                "HAZARDOUS",
                new BigDecimal("10.500"),
                null, null, null, null, null,
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10),
                "3", "UN1203", "ガソリン",
                null, null, null
        );
        when(shipperExistenceChecker.exists(shipperId)).thenReturn(true);
        ArgumentCaptor<Cargo> cargoCaptor = ArgumentCaptor.forClass(Cargo.class);

        BookingId bookingId = cargoBookingCommandService.bookCargo(command);

        assertNotNull(bookingId);
        verify(cargoRepository).save(cargoCaptor.capture());
        Cargo savedCargo = cargoCaptor.getValue();
        assertEquals(CargoType.HAZARDOUS, savedCargo.getCargoType());
        assertNotNull(savedCargo.getHazardousDeclaration());
        assertEquals("3", savedCargo.getHazardousDeclaration().hazardousClass());
        assertEquals("UN1203", savedCargo.getHazardousDeclaration().unNumber());
        assertEquals("ガソリン", savedCargo.getHazardousDeclaration().properShippingName());
    }

    @Test
    void shouldCreateRefrigeratedCargoFromCommand() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        BookCargoCommand command = new BookCargoCommand(
                shipperId.toString(),
                "REFRIGERATED",
                new BigDecimal("10.500"),
                null, null, null, null, null,
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10),
                null, null, null,
                new BigDecimal("-25"), new BigDecimal("-18"), "CELSIUS"
        );
        when(shipperExistenceChecker.exists(shipperId)).thenReturn(true);
        ArgumentCaptor<Cargo> cargoCaptor = ArgumentCaptor.forClass(Cargo.class);

        BookingId bookingId = cargoBookingCommandService.bookCargo(command);

        assertNotNull(bookingId);
        verify(cargoRepository).save(cargoCaptor.capture());
        Cargo savedCargo = cargoCaptor.getValue();
        assertEquals(CargoType.REFRIGERATED, savedCargo.getCargoType());
        assertNotNull(savedCargo.getTemperatureRequirement());
        assertEquals(new BigDecimal("-25"), savedCargo.getTemperatureRequirement().minTemperature());
        assertEquals(new BigDecimal("-18"), savedCargo.getTemperatureRequirement().maxTemperature());
        assertEquals(TemperatureUnit.CELSIUS, savedCargo.getTemperatureRequirement().unit());
    }

    @Test
    void shouldConfirmBooking_whenStatusIsPreliminary() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        Cargo cargo = cargo(bookingId, BookingStatus.PRELIMINARY);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        cargoBookingCommandService.confirmBooking(new ConfirmBookingCommand(bookingId.toString()));

        ArgumentCaptor<Cargo> captor = ArgumentCaptor.forClass(Cargo.class);
        verify(cargoRepository).updateStatus(captor.capture());
        assertEquals(BookingStatus.CONFIRMED, captor.getValue().getStatus());
        verify(eventPublisher).publishEvent(any(BookingConfirmedEvent.class));
    }

    @Test
    void shouldThrowBookingNotFoundException_whenConfirmingNonExistentBooking() {
        String unknownId = UUID.randomUUID().toString();
        ConfirmBookingCommand command = new ConfirmBookingCommand(unknownId);
        when(cargoRepository.findByBookingId(any())).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () -> cargoBookingCommandService.confirmBooking(command));
        verify(cargoRepository, never()).updateStatus(any());
    }

    @Test
    void shouldThrowIllegalStateException_whenConfirmingAlreadyConfirmedBooking() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        ConfirmBookingCommand command = new ConfirmBookingCommand(bookingId.toString());
        Cargo cargo = cargo(bookingId, BookingStatus.CONFIRMED);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        assertThrows(IllegalStateException.class, () -> cargoBookingCommandService.confirmBooking(command));
        verify(cargoRepository, never()).updateStatus(any());
    }

    @Test
    void shouldCancelBooking_fromPreliminaryStatus() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        Cargo cargo = cargo(bookingId, BookingStatus.PRELIMINARY);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        cargoBookingCommandService.cancelBooking(new CancelBookingCommand(bookingId.toString()));

        ArgumentCaptor<Cargo> captor = ArgumentCaptor.forClass(Cargo.class);
        verify(cargoRepository).updateStatus(captor.capture());
        assertEquals(BookingStatus.CANCELLED, captor.getValue().getStatus());
        verify(eventPublisher).publishEvent(any(BookingCancelledEvent.class));
    }

    @Test
    void shouldCancelBooking_fromConfirmedStatus() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        Cargo cargo = cargo(bookingId, BookingStatus.CONFIRMED);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        cargoBookingCommandService.cancelBooking(new CancelBookingCommand(bookingId.toString()));

        ArgumentCaptor<Cargo> captor = ArgumentCaptor.forClass(Cargo.class);
        verify(cargoRepository).updateStatus(captor.capture());
        assertEquals(BookingStatus.CANCELLED, captor.getValue().getStatus());
    }

    @Test
    void shouldThrowIllegalStateException_whenCancellingSettledBooking() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        CancelBookingCommand command = new CancelBookingCommand(bookingId.toString());
        Cargo cargo = cargo(bookingId, BookingStatus.SETTLED);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        assertThrows(IllegalStateException.class, () -> cargoBookingCommandService.cancelBooking(command));
        verify(cargoRepository, never()).updateStatus(any());
    }

    @Test
    void shouldThrowIllegalStateException_whenCancellingAlreadyCancelledBooking() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        CancelBookingCommand command = new CancelBookingCommand(bookingId.toString());
        Cargo cargo = cargo(bookingId, BookingStatus.CANCELLED);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        assertThrows(IllegalStateException.class, () -> cargoBookingCommandService.cancelBooking(command));
        verify(cargoRepository, never()).updateStatus(any());
    }

    @Test
    void shouldThrowBookingNotFoundException_whenCancellingNonExistentBooking() {
        String unknownId = UUID.randomUUID().toString();
        CancelBookingCommand command = new CancelBookingCommand(unknownId);
        when(cargoRepository.findByBookingId(any())).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () -> cargoBookingCommandService.cancelBooking(command));
        verify(cargoRepository, never()).updateStatus(any());
    }

    @Test
    void shouldCreateCargoWithDimensionsQuantityAndDescription() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        BookCargoCommand command = new BookCargoCommand(
                shipperId.toString(),
                "GENERAL",
                new BigDecimal("10.500"),
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("3"),
                5,
                "電子部品",
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10),
                null, null, null,
                null, null, null
        );
        when(shipperExistenceChecker.exists(shipperId)).thenReturn(true);
        ArgumentCaptor<Cargo> cargoCaptor = ArgumentCaptor.forClass(Cargo.class);

        BookingId bookingId = cargoBookingCommandService.bookCargo(command);

        assertNotNull(bookingId);
        verify(cargoRepository).save(cargoCaptor.capture());
        Cargo savedCargo = cargoCaptor.getValue();
        assertNotNull(savedCargo.getDimensions());
        assertEquals(new BigDecimal("10"), savedCargo.getDimensions().length());
        assertEquals(new BigDecimal("5"), savedCargo.getDimensions().width());
        assertEquals(new BigDecimal("3"), savedCargo.getDimensions().height());
        assertNotNull(savedCargo.getQuantity());
        assertEquals(5, savedCargo.getQuantity().value());
        assertNotNull(savedCargo.getDescription());
        assertEquals("電子部品", savedCargo.getDescription().value());
    }

    @Test
    void shouldCreateCargoWithPartialDimensions_shouldBeNull() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        BookCargoCommand command = new BookCargoCommand(
                shipperId.toString(),
                "GENERAL",
                new BigDecimal("10.500"),
                new BigDecimal("10"), null, null,
                null, null,
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10),
                null, null, null,
                null, null, null
        );
        when(shipperExistenceChecker.exists(shipperId)).thenReturn(true);
        ArgumentCaptor<Cargo> cargoCaptor = ArgumentCaptor.forClass(Cargo.class);

        cargoBookingCommandService.bookCargo(command);

        verify(cargoRepository).save(cargoCaptor.capture());
        Cargo savedCargo = cargoCaptor.getValue();
        assertEquals(null, savedCargo.getDimensions());
    }

    @Test
    void shouldThrowWhenRefrigeratedCargoCommandMissingTemperature() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        BookCargoCommand command = new BookCargoCommand(
                shipperId.toString(),
                "REFRIGERATED",
                new BigDecimal("10.500"),
                null, null, null, null, null,
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10),
                null, null, null,
                null, null, null
        );
        when(shipperExistenceChecker.exists(shipperId)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> cargoBookingCommandService.bookCargo(command)
        );

        verify(cargoRepository, never()).save(any(Cargo.class));
    }

    @Test
    void shouldThrowWhenHazardousCargoCommandMissingDeclaration() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        BookCargoCommand command = new BookCargoCommand(
                shipperId.toString(),
                "HAZARDOUS",
                new BigDecimal("10.500"),
                null, null, null, null, null,
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10),
                null, null, null,
                null, null, null
        );
        when(shipperExistenceChecker.exists(shipperId)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> cargoBookingCommandService.bookCargo(command)
        );

        verify(cargoRepository, never()).save(any(Cargo.class));
    }

    @Test
    void assignToRouting_仮受付状態から経路設計中に遷移する() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        Cargo preliminary = cargo(bookingId, BookingStatus.PRELIMINARY);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(preliminary));

        cargoBookingCommandService.assignToRouting(new AssignToRoutingCommand(bookingId.toString()));

        ArgumentCaptor<Cargo> captor = ArgumentCaptor.forClass(Cargo.class);
        verify(cargoRepository).updateStatus(captor.capture());
        assertEquals(BookingStatus.ROUTE_PROPOSED, captor.getValue().getStatus());
        verify(eventPublisher).publishEvent(any(BookingAssignedToRoutingEvent.class));
    }

    @Test
    void assignToRouting_存在しないBookingIdは例外() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class,
                () -> cargoBookingCommandService.assignToRouting(new AssignToRoutingCommand(bookingId.toString())));
    }

    private Cargo cargo(BookingId bookingId, BookingStatus status) {
        return new Cargo(
                bookingId,
                new ShipperId(UUID.randomUUID()),
                CargoType.GENERAL,
                new BigDecimal("10.500"),
                RouteSpecification.fromUnLocodes("JPTYO", "USLAX", LocalDate.now().plusDays(10)),
                status
        );
    }
}
