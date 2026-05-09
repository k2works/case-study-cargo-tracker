package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.domain.events.TrackingNumberIssuedEvent;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.bookingms.domain.model.valueobjects.Weight;
import com.example.bookingms.domain.ports.CargoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingNumberIssuedEventListener テスト")
class TrackingNumberIssuedEventListenerTest {

    @Mock
    private CargoRepository cargoRepository;

    private TrackingNumberIssuedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TrackingNumberIssuedEventListener(cargoRepository);
    }

    @Test
    @DisplayName("CONFIRMED 状態の貨物に対してイベント受信時に TRACKING_ISSUED に遷移すること")
    void shouldTransitionToTrackingIssuedWhenCargoIsConfirmed() {
        String bookingId = "BK-001234";
        Cargo cargo = new Cargo(1L, new BookingId(bookingId), 1L,
                BookingStatus.CONFIRMED, CargoType.GENERAL,
                new Weight(BigDecimal.valueOf(100)),
                new RouteSpecification("JPTYO", "CNSHA", LocalDate.of(2026, 6, 30)));

        when(cargoRepository.findByBookingId(new BookingId(bookingId))).thenReturn(Optional.of(cargo));

        listener.handleTrackingNumberIssued(new TrackingNumberIssuedEvent(bookingId, "TRK-000001"));

        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
        verify(cargoRepository).update(cargo);
    }

    @Test
    @DisplayName("存在しない予約 ID のイベントを受信した場合は何もしないこと")
    void shouldDoNothingWhenCargoNotFound() {
        String bookingId = "BK-UNKNOWN";
        when(cargoRepository.findByBookingId(new BookingId(bookingId))).thenReturn(Optional.empty());

        listener.handleTrackingNumberIssued(new TrackingNumberIssuedEvent(bookingId, "TRK-000001"));

        verify(cargoRepository, never()).update(any());
    }

    @Test
    @DisplayName("CONFIRMED でない状態の貨物に対しては状態遷移しないこと")
    void shouldNotTransitionWhenCargoIsNotConfirmed() {
        String bookingId = "BK-001234";
        Cargo cargo = new Cargo(1L, new BookingId(bookingId), 1L,
                BookingStatus.PRELIMINARY, CargoType.GENERAL,
                new Weight(BigDecimal.valueOf(100)),
                new RouteSpecification("JPTYO", "CNSHA", LocalDate.of(2026, 6, 30)));

        when(cargoRepository.findByBookingId(new BookingId(bookingId))).thenReturn(Optional.of(cargo));

        listener.handleTrackingNumberIssued(new TrackingNumberIssuedEvent(bookingId, "TRK-000001"));

        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
        verify(cargoRepository, never()).update(any());
    }
}
