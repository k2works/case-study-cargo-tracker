package com.example.cargotracker.trackingms.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.trackingms.domain.model.commands.InitializeTrackingCommand;
import com.example.cargotracker.trackingms.domain.model.commands.UpdateTransportStatusCommand;
import com.example.cargotracker.trackingms.domain.model.events.TrackingInitializedEvent;
import com.example.cargotracker.trackingms.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.trackingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.trackingms.domain.model.valueobjects.EventSource;
import com.example.cargotracker.trackingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Leg;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * TrackingActivity Aggregate のユニットテスト（US18）。
 *
 * <p>{@link EventAppender} をモック化し、Command Handler と {@code @EventSourcingHandler}
 * を直接検証する。</p>
 */
@DisplayName("TrackingActivity Aggregate（ユニット）")
class TrackingActivityTest {

    private static final TrackingNumber TRK = new TrackingNumber("TRK-20260101-ABC12345");
    private static final String BOOKING_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Location TOKYO = Location.of("JPTYO");
    private static final Location HAMBURG = Location.of("DEHAM");
    private static final LocalDateTime ETA = LocalDateTime.of(2026, 8, 10, 14, 30);

    private static CargoItinerary tokyoToHamburgItinerary() {
        return new CargoItinerary(List.of(new Leg(
                "V-MOL-001",
                TOKYO,
                HAMBURG,
                LocalDateTime.of(2026, 7, 20, 14, 0),
                ETA)));
    }

    @Test
    @DisplayName("InitializeTrackingCommand で TrackingInitializedEvent が発行される")
    void initialize_発行イベント() {
        EventAppender appender = mock(EventAppender.class);
        var itinerary = tokyoToHamburgItinerary();
        var command = new InitializeTrackingCommand(
                TRK.value(), BOOKING_ID, itinerary, TOKYO, HAMBURG, ETA);

        String returned = TrackingActivity.initialize(command, appender);

        assertThat(returned).isEqualTo(TRK.value());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TrackingInitializedEvent.class);
        var event = (TrackingInitializedEvent) captor.getValue();
        assertThat(event.trackingNumber()).isEqualTo(TRK);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.itinerary()).isEqualTo(itinerary);
        assertThat(event.initialStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
    }

    @Test
    @DisplayName("TrackingInitializedEvent を再生すると状態が復元される")
    void on_initialized_イベント再生() {
        var activity = new TrackingActivity();
        var itinerary = tokyoToHamburgItinerary();
        var event = new TrackingInitializedEvent(
                TRK, BOOKING_ID, itinerary, TOKYO, HAMBURG, ETA,
                TransportStatus.NOT_RECEIVED);

        activity.on(event);

        assertThat(activity.getTrackingNumber()).isEqualTo(TRK);
        assertThat(activity.getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(activity.getCurrentStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
        assertThat(activity.getEstimatedArrival()).isEqualTo(ETA);
        assertThat(activity.isMisrouted()).isFalse();
    }

    @Test
    @DisplayName("UpdateTransportStatusCommand で TransportStatusUpdatedEvent が発行される")
    void updateStatus_発行イベント() {
        EventAppender appender = mock(EventAppender.class);
        // 初期化済みの状態を再現
        var initialized = new TrackingInitializedEvent(
                TRK, BOOKING_ID, tokyoToHamburgItinerary(), TOKYO, HAMBURG, ETA,
                TransportStatus.NOT_RECEIVED);
        var activity = new TrackingActivity();
        activity.on(initialized);

        var updatedAt = LocalDateTime.of(2026, 7, 25, 8, 0);
        var command = new UpdateTransportStatusCommand(
                TRK.value(),
                TransportStatus.IN_TRANSIT,
                Location.of("SGSIN"),
                updatedAt,
                new HandlerId("admin-001"));

        activity.updateStatus(command, appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TransportStatusUpdatedEvent.class);
        var event = (TransportStatusUpdatedEvent) captor.getValue();
        assertThat(event.trackingNumber()).isEqualTo(TRK);
        assertThat(event.newStatus()).isEqualTo(TransportStatus.IN_TRANSIT);
        assertThat(event.location()).isEqualTo(Location.of("SGSIN"));
        assertThat(event.updatedAt()).isEqualTo(updatedAt);
        assertThat(event.operatorId()).isEqualTo(new HandlerId("admin-001"));
        assertThat(event.source()).isEqualTo(EventSource.MANUAL);
    }

    @Test
    @DisplayName("TransportStatusUpdatedEvent を再生すると状態が更新される")
    void on_updated_イベント再生() {
        var activity = new TrackingActivity();
        activity.on(new TrackingInitializedEvent(
                TRK, BOOKING_ID, tokyoToHamburgItinerary(), TOKYO, HAMBURG, ETA,
                TransportStatus.NOT_RECEIVED));
        activity.on(new TransportStatusUpdatedEvent(
                TRK, TransportStatus.IN_TRANSIT, Location.of("SGSIN"),
                LocalDateTime.of(2026, 7, 25, 8, 0), new HandlerId("admin-001"),
                EventSource.MANUAL));

        assertThat(activity.getCurrentStatus()).isEqualTo(TransportStatus.IN_TRANSIT);
        assertThat(activity.getCurrentLocation()).isEqualTo(Location.of("SGSIN"));
    }

    @Test
    @DisplayName("未初期化 Aggregate に対する updateStatus は IllegalStateException を送出する")
    void updateStatus_未初期化AggregateはIllegalStateException() {
        EventAppender appender = mock(EventAppender.class);
        var activity = new TrackingActivity();

        var command = new UpdateTransportStatusCommand(
                TRK.value(),
                TransportStatus.IN_TRANSIT,
                Location.of("SGSIN"),
                LocalDateTime.of(2026, 7, 25, 8, 0),
                new HandlerId("admin-001"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> activity.updateStatus(command, appender));
    }

    @Test
    @DisplayName("DELIVERED 状態に遷移すると deliveredAt が記録される")
    void on_updated_配送完了で記録() {
        var activity = new TrackingActivity();
        activity.on(new TrackingInitializedEvent(
                TRK, BOOKING_ID, tokyoToHamburgItinerary(), TOKYO, HAMBURG, ETA,
                TransportStatus.NOT_RECEIVED));
        var deliveredAt = LocalDateTime.of(2026, 8, 10, 16, 0);
        activity.on(new TransportStatusUpdatedEvent(
                TRK, TransportStatus.DELIVERED, HAMBURG, deliveredAt,
                new HandlerId("admin-001"), EventSource.HANDLING));

        assertThat(activity.getCurrentStatus()).isEqualTo(TransportStatus.DELIVERED);
        assertThat(activity.getDeliveredAt()).isEqualTo(deliveredAt);
    }
}
