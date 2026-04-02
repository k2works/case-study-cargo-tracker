package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.tracking.application.internal.outboundservices.BookingInfoQueryPort;
import com.example.cargotracker.tracking.application.internal.outboundservices.BookingInfoQueryPort;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;
import com.example.cargotracker.tracking.domain.model.valueobjects.HandlingEventView;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingQueryService")
class TrackingQueryServiceTest {

    @Mock
    private TrackingRepository trackingRepository;

    @Mock
    private BookingInfoQueryPort bookingInfoQueryPort;

    private TrackingQueryService trackingQueryService;

    @BeforeEach
    void setUp() {
        trackingQueryService = new TrackingQueryService(trackingRepository, bookingInfoQueryPort);
    }

    @Test
    @DisplayName("追跡番号で TrackingEntry を取得できる")
    void findByTrackingNumber_returnsEntry() {
        UUID bookingId = UUID.randomUUID();
        TrackingEntry entry = new TrackingEntry(new TrackingNumber("TRK-ABC12345"), bookingId);
        when(trackingRepository.findByTrackingNumber(new TrackingNumber("TRK-ABC12345")))
                .thenReturn(Optional.of(entry));

        Optional<TrackingEntry> result = trackingQueryService.findByTrackingNumber("TRK-ABC12345");

        assertThat(result).isPresent();
        assertThat(result.get().getTrackingNumber().value()).isEqualTo("TRK-ABC12345");
    }

    @Test
    @DisplayName("荷役履歴が含まれる TrackingInfoDto を取得できる")
    void findTrackingInfo_includesHandlingHistory() {
        UUID bookingId = UUID.randomUUID();
        TrackingNumber tn = new TrackingNumber("TRK-ABC12345");
        TrackingEntry entry = new TrackingEntry(tn, bookingId);
        LocalDateTime completionTime = LocalDateTime.of(2026, 5, 1, 9, 0);

        when(trackingRepository.findByTrackingNumber(tn)).thenReturn(Optional.of(entry));
        when(trackingRepository.findHandlingEventsByTrackingNumber(tn)).thenReturn(List.of(
                new HandlingEventView(completionTime, "JPTYO", "LOAD", null),
                new HandlingEventView(completionTime.minusDays(1), "JPTYO", "RECEIVE", "引取メモ")
        ));
        when(bookingInfoQueryPort.findById(bookingId)).thenReturn(
                Optional.of(new BookingInfoQueryPort.BookingSummary("JPTYO", "SGSIN", LocalDate.of(2026, 6, 1)))
        );

        Optional<TrackingInfoDto> result = trackingQueryService.findTrackingInfo("TRK-ABC12345");

        assertThat(result).isPresent();
        TrackingInfoDto dto = result.get();
        assertThat(dto.trackingNumber()).isEqualTo("TRK-ABC12345");
        assertThat(dto.bookingId()).isEqualTo(bookingId);
        assertThat(dto.originLocation()).isEqualTo("JPTYO");
        assertThat(dto.destinationLocation()).isEqualTo("SGSIN");
        assertThat(dto.estimatedArrival()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(dto.currentState()).isEqualTo("積み込み");
        assertThat(dto.handlingHistory()).hasSize(2);
        assertThat(dto.handlingHistory().get(0).eventType()).isEqualTo("LOAD");
        assertThat(dto.handlingHistory().get(0).eventTypeDisplayName()).isEqualTo("積み込み");
        assertThat(dto.handlingHistory().get(1).eventType()).isEqualTo("RECEIVE");
        assertThat(dto.handlingHistory().get(1).memo()).isEqualTo("引取メモ");
    }

    @Test
    @DisplayName("荷役履歴がない場合は空リストを返し currentState は「未受取」になる")
    void findTrackingInfo_noHandlingHistory_returnsEmptyList() {
        UUID bookingId = UUID.randomUUID();
        TrackingNumber tn = new TrackingNumber("TRK-ABC12345");
        TrackingEntry entry = new TrackingEntry(tn, bookingId);
        when(trackingRepository.findByTrackingNumber(tn)).thenReturn(Optional.of(entry));
        when(trackingRepository.findHandlingEventsByTrackingNumber(tn)).thenReturn(List.of());
        when(bookingInfoQueryPort.findById(any())).thenReturn(
                Optional.of(new BookingInfoQueryPort.BookingSummary("JPTYO", "SGSIN", LocalDate.of(2026, 6, 1)))
        );

        Optional<TrackingInfoDto> result = trackingQueryService.findTrackingInfo("TRK-ABC12345");

        assertThat(result).isPresent();
        assertThat(result.get().handlingHistory()).isEmpty();
        assertThat(result.get().currentState()).isEqualTo("未受取");
    }

    @Test
    @DisplayName("存在しない追跡番号の場合は Empty を返す")
    void findTrackingInfo_unknownTrackingNumber_returnsEmpty() {
        TrackingNumber tn = new TrackingNumber("TRK-UNKNOWN0");
        when(trackingRepository.findByTrackingNumber(tn)).thenReturn(Optional.empty());

        Optional<TrackingInfoDto> result = trackingQueryService.findTrackingInfo("TRK-UNKNOWN0");

        assertThat(result).isEmpty();
    }
}
