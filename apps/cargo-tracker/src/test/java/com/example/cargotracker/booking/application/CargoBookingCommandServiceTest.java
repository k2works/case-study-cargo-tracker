package com.example.cargotracker.booking.application;

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
import com.example.cargotracker.shared.domain.model.Location;
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
        Cargo cargo = new Cargo(bookingId, new ShipperId(UUID.randomUUID()), CargoType.GENERAL,
                new BigDecimal("10.500"), null, null, null,
                new RouteSpecification(new Location("JPTYO"), new Location("USLAX"), LocalDate.now().plusDays(10)),
                BookingStatus.PRELIMINARY, null, null);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        cargoBookingCommandService.confirmBooking(new ConfirmBookingCommand(bookingId.toString()));

        ArgumentCaptor<Cargo> captor = ArgumentCaptor.forClass(Cargo.class);
        verify(cargoRepository).update(captor.capture());
        assertEquals(BookingStatus.CONFIRMED, captor.getValue().getStatus());
    }

    @Test
    void shouldThrowBookingNotFoundException_whenConfirmingNonExistentBooking() {
        String unknownId = UUID.randomUUID().toString();
        when(cargoRepository.findByBookingId(any())).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class,
                () -> cargoBookingCommandService.confirmBooking(new ConfirmBookingCommand(unknownId)));
        verify(cargoRepository, never()).update(any());
    }

    @Test
    void shouldThrowIllegalStateException_whenConfirmingAlreadyConfirmedBooking() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        Cargo cargo = new Cargo(bookingId, new ShipperId(UUID.randomUUID()), CargoType.GENERAL,
                new BigDecimal("10.500"), null, null, null,
                new RouteSpecification(new Location("JPTYO"), new Location("USLAX"), LocalDate.now().plusDays(10)),
                BookingStatus.CONFIRMED, null, null);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        assertThrows(IllegalStateException.class,
                () -> cargoBookingCommandService.confirmBooking(new ConfirmBookingCommand(bookingId.toString())));
        verify(cargoRepository, never()).update(any());
    }

    @Test
    void shouldCancelBooking_fromPreliminaryStatus() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        Cargo cargo = new Cargo(bookingId, new ShipperId(UUID.randomUUID()), CargoType.GENERAL,
                new BigDecimal("10.500"), null, null, null,
                new RouteSpecification(new Location("JPTYO"), new Location("USLAX"), LocalDate.now().plusDays(10)),
                BookingStatus.PRELIMINARY, null, null);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        cargoBookingCommandService.cancelBooking(new CancelBookingCommand(bookingId.toString()));

        ArgumentCaptor<Cargo> captor = ArgumentCaptor.forClass(Cargo.class);
        verify(cargoRepository).update(captor.capture());
        assertEquals(BookingStatus.CANCELLED, captor.getValue().getStatus());
    }

    @Test
    void shouldThrowIllegalStateException_whenCancellingAlreadyCancelledBooking() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        Cargo cargo = new Cargo(bookingId, new ShipperId(UUID.randomUUID()), CargoType.GENERAL,
                new BigDecimal("10.500"), null, null, null,
                new RouteSpecification(new Location("JPTYO"), new Location("USLAX"), LocalDate.now().plusDays(10)),
                BookingStatus.CANCELLED, null, null);
        when(cargoRepository.findByBookingId(bookingId)).thenReturn(Optional.of(cargo));

        assertThrows(IllegalStateException.class,
                () -> cargoBookingCommandService.cancelBooking(new CancelBookingCommand(bookingId.toString())));
        verify(cargoRepository, never()).update(any());
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
}
