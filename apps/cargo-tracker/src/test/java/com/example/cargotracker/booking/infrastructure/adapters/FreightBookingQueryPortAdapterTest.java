package com.example.cargotracker.booking.infrastructure.adapters;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.repository.HandlingEventRepository;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FreightBookingQueryPortAdapter")
class FreightBookingQueryPortAdapterTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private HandlingEventRepository handlingEventRepository;

    @Test
    @DisplayName("RECEIVE がある確定済み予約は算出対象として荷役件数を返す")
    void findCalculableBookingById_returnsSummaryWithHandlingCount() {
        Booking booking = confirmedBooking();
        HandlingEvent receiveEvent = HandlingEvent.recordEvent(
                HandlingEventId.generate(),
                booking.getId().value(),
                HandlingEventType.RECEIVE,
                "SGSIN",
                LocalDateTime.of(2026, 4, 3, 10, 0),
                "配送完了",
                "RC-US16-001"
        );

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(handlingEventRepository.findByBookingId(booking.getId().value())).thenReturn(List.of(receiveEvent));

        FreightBookingQueryPortAdapter adapter = new FreightBookingQueryPortAdapter(
                bookingRepository,
                handlingEventRepository
        );

        var result = adapter.findCalculableBookingById(booking.getId().value().toString());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().handlingEventCount()).isEqualTo(1);
        assertThat(result.orElseThrow().routePath()).isEqualTo("JPTYO/SGSIN");
    }

    @Test
    @DisplayName("RECEIVE がない確定済み予約は算出対象にしない")
    void findCalculableBookingById_returnsEmptyWhenReceiveDoesNotExist() {
        Booking booking = confirmedBooking();

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(handlingEventRepository.findByBookingId(booking.getId().value())).thenReturn(List.of());

        FreightBookingQueryPortAdapter adapter = new FreightBookingQueryPortAdapter(
                bookingRepository,
                handlingEventRepository
        );

        var result = adapter.findCalculableBookingById(booking.getId().value().toString());

        assertThat(result).isEmpty();
    }

    private Booking confirmedBooking() {
        Booking booking = Booking.register(
                BookingId.generate(),
                ShipperId.generate(),
                new CargoSpecification(
                        CargoType.GENERAL_CARGO,
                        new BigDecimal("180"),
                        null,
                        null,
                        null,
                        1,
                        "料金算出テスト貨物"
                ),
                new TransportCondition(
                        "JPTYO",
                        "SGSIN",
                        LocalDate.of(2026, 4, 7),
                        LocalDate.of(2026, 4, 19)
                )
        );
        booking.assignRoute(new AssignedRoute(
                "VOY-001",
                "JPTYO/SGSIN",
                LocalDate.of(2026, 4, 19)
        ));
        booking.confirm();
        return booking;
    }
}
