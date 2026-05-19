package com.example.cargotracker.trackingms.application.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.trackingms.domain.model.events.TrackingInitializedEvent;
import com.example.cargotracker.trackingms.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.trackingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.trackingms.domain.model.valueobjects.EventSource;
import com.example.cargotracker.trackingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Leg;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventRecord;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrackingProjectionsEventHandler")
class TrackingProjectionsEventHandlerTest {

    private static final TrackingNumber TRK = new TrackingNumber("TRK-20260101-ABC12345");
    private static final Location TOKYO = Location.of("JPTYO");
    private static final Location HAMBURG = Location.of("DEHAM");
    private static final LocalDateTime ETA = LocalDateTime.of(2026, 8, 10, 14, 30);

    @Mock
    private TrackingSummaryMapper summaryMapper;

    @Mock
    private TrackingEventMapper eventMapper;

    private TrackingProjectionsEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TrackingProjectionsEventHandler(summaryMapper, eventMapper);
    }

    private static CargoItinerary itinerary() {
        return new CargoItinerary(List.of(new Leg(
                "V-MOL-001", TOKYO, HAMBURG, LocalDateTime.of(2026, 7, 20, 14, 0), ETA)));
    }

    @Test
    @DisplayName("TrackingInitializedEvent で tracking_summary が INSERT され初期 tracking_event が記録される")
    void on_initialized() {
        var event = new TrackingInitializedEvent(
                TRK, "B-001", itinerary(), TOKYO, HAMBURG, ETA, TransportStatus.NOT_RECEIVED);

        handler.on(event);

        ArgumentCaptor<TrackingSummaryRecord> summaryCaptor =
                ArgumentCaptor.forClass(TrackingSummaryRecord.class);
        verify(summaryMapper).insert(summaryCaptor.capture());
        var summary = summaryCaptor.getValue();
        assertThat(summary.trackingNumber()).isEqualTo(TRK.value());
        assertThat(summary.bookingId()).isEqualTo("B-001");
        assertThat(summary.currentStatus()).isEqualTo("NOT_RECEIVED");
        assertThat(summary.originUnlocode()).isEqualTo("JPTYO");
        assertThat(summary.destinationUnlocode()).isEqualTo("DEHAM");
        assertThat(summary.misrouted()).isFalse();

        ArgumentCaptor<TrackingEventRecord> eventCaptor =
                ArgumentCaptor.forClass(TrackingEventRecord.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("TRACKING_INITIALIZED");
        assertThat(eventCaptor.getValue().source()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("TransportStatusUpdatedEvent で tracking_summary が更新され tracking_event が追記される")
    void on_statusUpdated() {
        var updatedAt = LocalDateTime.of(2026, 7, 25, 8, 0);
        var event = new TransportStatusUpdatedEvent(
                TRK, TransportStatus.IN_TRANSIT, Location.of("SGSIN"),
                updatedAt, new HandlerId("admin-001"), EventSource.MANUAL);

        handler.on(event);

        verify(summaryMapper).updateCurrentStatus(
                TRK.value(), "IN_TRANSIT", "SGSIN", null, false, updatedAt);
        verify(summaryMapper, never()).updateDeliveredAt(any(), any());

        ArgumentCaptor<TrackingEventRecord> eventCaptor =
                ArgumentCaptor.forClass(TrackingEventRecord.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("STATUS_UPDATE");
        assertThat(eventCaptor.getValue().source()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("DELIVERED 状態への遷移で updateDeliveredAt も呼ばれる")
    void on_statusUpdated_delivered() {
        var deliveredAt = LocalDateTime.of(2026, 8, 10, 16, 0);
        var event = new TransportStatusUpdatedEvent(
                TRK, TransportStatus.DELIVERED, HAMBURG,
                deliveredAt, new HandlerId("handler-001"), EventSource.HANDLING);

        handler.on(event);

        verify(summaryMapper).updateCurrentStatus(
                TRK.value(), "DELIVERED", "DEHAM", null, false, deliveredAt);
        verify(summaryMapper).updateDeliveredAt(TRK.value(), deliveredAt);
    }

    @Test
    @DisplayName("MISROUTED 状態への遷移で misrouted=true で更新される")
    void on_statusUpdated_misrouted() {
        var updatedAt = LocalDateTime.of(2026, 7, 25, 8, 0);
        var event = new TransportStatusUpdatedEvent(
                TRK, TransportStatus.MISROUTED, Location.of("SGSIN"),
                updatedAt, new HandlerId("admin-001"), EventSource.SYSTEM);

        handler.on(event);

        verify(summaryMapper).updateCurrentStatus(
                TRK.value(), "MISROUTED", "SGSIN", null, true, updatedAt);
    }
}
