package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.event.BookingRouteAssignedEvent;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.commands.AssignRouteCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssignRouteCommandService")
class AssignRouteCommandServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AssignRouteCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new AssignRouteCommandService(bookingRepository, eventPublisher);
    }

    @Test
    @DisplayName("ルートを割り当てると予約を保存する")
    void assignRoute() {
        Booking booking = provisionalBooking();
        AssignRouteCommand command = new AssignRouteCommand(
                booking.getId().value(),
                "VOY-001",
                "JPTYO/USNYC",
                LocalDate.of(2025, 9, 1)
        );
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        commandService.execute(command);

        assertThat(booking.getAssignedRoute()).isNotNull();
        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("ルート割り当て後にイベントを発行する")
    void publishEventAfterAssignRoute() {
        Booking booking = provisionalBooking();
        AssignRouteCommand command = new AssignRouteCommand(
                booking.getId().value(),
                "VOY-001",
                "JPTYO/USNYC",
                LocalDate.of(2025, 9, 1)
        );
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        commandService.execute(command);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).anyMatch(BookingRouteAssignedEvent.class::isInstance);
    }

    @Test
    @DisplayName("予約が存在しない場合は例外を投げる")
    void throwWhenBookingNotFound() {
        BookingId bookingId = BookingId.generate();
        AssignRouteCommand command = new AssignRouteCommand(
                bookingId.value(),
                "VOY-001",
                "JPTYO/USNYC",
                LocalDate.of(2025, 9, 1)
        );
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commandService.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("予約が見つかりません");
        verify(bookingRepository, never()).save(any());
    }

    private Booking provisionalBooking() {
        return Booking.register(
                BookingId.generate(),
                ShipperId.generate(),
                new CargoSpecification(CargoType.GENERAL_CARGO, new BigDecimal("100"), null, null, null, 1, null),
                new TransportCondition("JPTYO", "USNYC", LocalDate.of(2025, 8, 1), LocalDate.of(2025, 9, 1))
        );
    }
}
