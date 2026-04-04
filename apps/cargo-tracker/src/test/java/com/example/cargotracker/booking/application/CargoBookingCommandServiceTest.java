package com.example.cargotracker.booking.application;

import com.example.cargotracker.booking.application.internal.commandservices.BookCargoCommand;
import com.example.cargotracker.booking.application.internal.commandservices.CargoBookingCommandService;
import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.repository.CargoRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.repository.ShipperRepository;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private ShipperRepository shipperRepository;

    @InjectMocks
    private CargoBookingCommandService cargoBookingCommandService;

    @Test
    void 有効なコマンドでCargoが保存されBookingIdが返る() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        BookCargoCommand command = new BookCargoCommand(
                shipperId.toString(),
                "GENERAL",
                new BigDecimal("10.500"),
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10)
        );
        when(shipperRepository.findById(shipperId)).thenReturn(Optional.of(org.mockito.Mockito.mock(Shipper.class)));
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
                "JPTYO",
                "USLAX",
                LocalDate.now().plusDays(10)
        );
        when(shipperRepository.findById(new ShipperId(UUID.fromString(shipperId)))).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> cargoBookingCommandService.bookCargo(command)
        );

        assertEquals("SHIPPER_NOT_FOUND", exception.getMessage());
        verify(cargoRepository, never()).save(any(Cargo.class));
    }
}
