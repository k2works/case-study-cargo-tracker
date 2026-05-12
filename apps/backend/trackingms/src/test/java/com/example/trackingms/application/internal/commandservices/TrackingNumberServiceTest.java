package com.example.trackingms.application.internal.commandservices;

import com.example.trackingms.domain.events.TrackingNumberIssuedEvent;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import com.example.trackingms.domain.ports.TrackingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingNumberService テスト")
class TrackingNumberServiceTest {

    @Mock
    private TrackingActivityRepository trackingActivityRepository;

    @Mock
    private TrackingEventPublisher trackingEventPublisher;

    private TrackingNumberService trackingNumberService;

    @BeforeEach
    void setUp() {
        trackingNumberService = new TrackingNumberService(trackingActivityRepository, trackingEventPublisher);
    }

    @Test
    @DisplayName("新規予約 ID に対して追跡番号を発行し、NOT_RECEIVED 状態で保存すること")
    void shouldIssueTrackingNumberForNewBookingId() {
        String bookingId = "BK-001234";
        TrackingActivity saved = new TrackingActivity(
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId(bookingId));

        when(trackingActivityRepository.findByBookingId(any())).thenReturn(Optional.empty());
        when(trackingActivityRepository.save(any())).thenReturn(saved);

        TrackingActivity result = trackingNumberService.issueTrackingNumber(bookingId);

        assertThat(result.getTransportStatus()).isEqualTo(TrackingStatus.NOT_RECEIVED);
        assertThat(result.getTrackingNumber().number()).startsWith("TRK-");
        verify(trackingActivityRepository).save(any());
    }

    @Test
    @DisplayName("新規追跡番号発行時に TrackingNumberIssuedEvent を publish すること")
    void shouldPublishTrackingNumberIssuedEventForNewBookingId() {
        String bookingId = "BK-001234";
        TrackingActivity saved = new TrackingActivity(
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId(bookingId));

        when(trackingActivityRepository.findByBookingId(any())).thenReturn(Optional.empty());
        when(trackingActivityRepository.save(any())).thenReturn(saved);

        trackingNumberService.issueTrackingNumber(bookingId);

        ArgumentCaptor<TrackingNumberIssuedEvent> captor = ArgumentCaptor.forClass(TrackingNumberIssuedEvent.class);
        verify(trackingEventPublisher).publishTrackingNumberIssued(captor.capture());

        TrackingNumberIssuedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.trackingNumber()).isEqualTo("TRK-000001");
    }

    @Test
    @DisplayName("既に発行済みの予約 ID に対しては既存の追跡番号を返すこと")
    void shouldReturnExistingTrackingNumberForAlreadyIssuedBookingId() {
        String bookingId = "BK-001234";
        TrackingActivity existing = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId(bookingId),
                TrackingStatus.NOT_RECEIVED,
                java.util.List.of());

        when(trackingActivityRepository.findByBookingId(any())).thenReturn(Optional.of(existing));

        TrackingActivity result = trackingNumberService.issueTrackingNumber(bookingId);

        assertThat(result.getTrackingNumber()).isEqualTo(new TrackingNumber("TRK-000001"));
        verify(trackingActivityRepository, never()).save(any());
    }

    @Test
    @DisplayName("既に発行済みの場合でも bookingms 側の状態追従のためイベントを再 publish すること")
    void shouldRepublishEventEvenWhenTrackingNumberAlreadyIssued() {
        String bookingId = "BK-001234";
        TrackingActivity existing = new TrackingActivity(
                1L,
                new TrackingNumber("TRK-000001"),
                new TrackingBookingId(bookingId),
                TrackingStatus.NOT_RECEIVED,
                java.util.List.of());

        when(trackingActivityRepository.findByBookingId(any())).thenReturn(Optional.of(existing));

        trackingNumberService.issueTrackingNumber(bookingId);

        ArgumentCaptor<TrackingNumberIssuedEvent> captor = ArgumentCaptor.forClass(TrackingNumberIssuedEvent.class);
        verify(trackingEventPublisher).publishTrackingNumberIssued(captor.capture());

        TrackingNumberIssuedEvent event = captor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.trackingNumber()).isEqualTo("TRK-000001");
    }
}
