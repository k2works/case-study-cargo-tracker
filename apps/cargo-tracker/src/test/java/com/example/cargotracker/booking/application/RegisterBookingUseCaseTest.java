package com.example.cargotracker.booking.application;

import com.example.cargotracker.booking.application.command.RegisterBookingCommand;
import com.example.cargotracker.booking.application.command.RegisterBookingUseCase;
import com.example.cargotracker.booking.domain.event.BookingRegisteredEvent;
import com.example.cargotracker.booking.domain.model.Booking;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.CargoType;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterBookingUseCase")
class RegisterBookingUseCaseTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ShipperExistencePort shipperExistencePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RegisterBookingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterBookingUseCase(bookingRepository, shipperExistencePort, eventPublisher);
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
                LocalDate.of(2025, 9, 1)
        );
    }

    @Test
    @DisplayName("予約を登録すると BookingId が返される")
    void registerBookingReturnsId() {
        ShipperId shipperId = ShipperId.generate();
        doNothing().when(shipperExistencePort).verifyExists(shipperId.value());

        BookingId result = useCase.execute(validCommand(shipperId.value()));

        assertThat(result).isNotNull();
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("荷主が存在しない場合は ShipperNotFoundException を投げる")
    void throwWhenShipperNotFound() {
        UUID unknownId = UUID.randomUUID();
        doThrow(new ShipperNotFoundException(unknownId.toString()))
                .when(shipperExistencePort).verifyExists(unknownId);

        assertThatThrownBy(() -> useCase.execute(validCommand(unknownId)))
                .isInstanceOf(ShipperNotFoundException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("登録後に BookingRegisteredEvent が発行される")
    void publishEventAfterRegistration() {
        ShipperId shipperId = ShipperId.generate();
        doNothing().when(shipperExistencePort).verifyExists(shipperId.value());

        useCase.execute(validCommand(shipperId.value()));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .anyMatch(e -> e instanceof BookingRegisteredEvent);
    }
}

