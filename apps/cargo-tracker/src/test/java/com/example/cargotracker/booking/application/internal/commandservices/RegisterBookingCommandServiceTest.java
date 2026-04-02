package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.outboundservices.ShipperExistencePort;
import com.example.cargotracker.booking.domain.event.BookingRegisteredEvent;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.commands.RegisterBookingCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterBookingCommandService")
class RegisterBookingCommandServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ShipperExistencePort shipperExistencePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RegisterBookingCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new RegisterBookingCommandService(bookingRepository, shipperExistencePort, eventPublisher);
    }

    private RegisterBookingCommand validCommand(UUID shipperId) {
        return new RegisterBookingCommand(
                shipperId,
                CargoType.GENERAL_CARGO,
                new BigDecimal("100.0"),
                null, null, null,
                1, "テスト品",
                "JPTYO", "USNYC",
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 9, 1),
                null, null, null, null
        );
    }

    @Test
    @DisplayName("予約を登録すると BookingId が返される")
    void registerBookingReturnsId() {
        ShipperId shipperId = ShipperId.generate();
        doNothing().when(shipperExistencePort).verifyExists(shipperId.value());

        BookingId result = commandService.execute(validCommand(shipperId.value()));

        assertThat(result).isNotNull();
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("荷主が存在しない場合は ShipperNotFoundException を投げる")
    void throwWhenShipperNotFound() {
        UUID unknownId = UUID.randomUUID();
        doThrow(new ShipperNotFoundException(unknownId.toString()))
                .when(shipperExistencePort).verifyExists(unknownId);

        assertThatThrownBy(() -> commandService.execute(validCommand(unknownId)))
                .isInstanceOf(ShipperNotFoundException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("登録後に BookingRegisteredEvent が発行される")
    void publishEventAfterRegistration() {
        ShipperId shipperId = ShipperId.generate();
        doNothing().when(shipperExistencePort).verifyExists(shipperId.value());

        commandService.execute(validCommand(shipperId.value()));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .anyMatch(BookingRegisteredEvent.class::isInstance);
    }
}
