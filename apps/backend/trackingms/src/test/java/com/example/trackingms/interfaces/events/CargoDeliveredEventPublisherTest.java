package com.example.trackingms.interfaces.events;

import com.example.shared.events.CargoDeliveredEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CargoDeliveredEventPublisher} のユニットテスト（US16 / IT5 4.2）。
 */
class CargoDeliveredEventPublisherTest {

    private EventGateway eventGateway;
    private TrackingSummaryMapper mapper;
    private CargoDeliveredEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventGateway = mock(EventGateway.class);
        mapper = mock(TrackingSummaryMapper.class);
        publisher = new CargoDeliveredEventPublisher(eventGateway, mapper);
    }

    private TrackingSummary summary() {
        TrackingSummary s = new TrackingSummary();
        s.setTrackingNumber("TRK-AB12CD3456");
        s.setBookingId("B-001");
        return s;
    }

    @Test
    @DisplayName("US16: DELIVERED 遷移時に CargoDeliveredEvent を発行する（H3 冪等化）")
    void DELIVERED遷移でCargoDeliveredEventを発行する() {
        when(mapper.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(summary());
        // 1 回目の publish 試行：markDeliveredPublished が 1 を返す（未発行）
        when(mapper.markDeliveredPublished(eq("TRK-AB12CD3456"), any(LocalDateTime.class)))
                .thenReturn(1);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 16, 14, 0);

        publisher.on(new TransportStatusUpdatedEvent(
                "TRK-AB12CD3456",
                TransportStatus.AWAITING_CLAIM, TransportStatus.DELIVERED,
                "USNYC", null, occurredAt, "荷受人引取"));

        ArgumentCaptor<CargoDeliveredEvent> captor = ArgumentCaptor.forClass(CargoDeliveredEvent.class);
        verify(eventGateway).publish(captor.capture());
        CargoDeliveredEvent e = captor.getValue();
        assertThat(e.trackingNumber()).isEqualTo("TRK-AB12CD3456");
        assertThat(e.bookingId()).isEqualTo("B-001");
        assertThat(e.deliveredAt()).isEqualTo(occurredAt);
        verify(mapper).markDeliveredPublished(eq("TRK-AB12CD3456"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("H3: 既に発行済み（markDeliveredPublished = 0）なら 2 回目は publish しない（冪等性）")
    void 既発行ならpublishしない() {
        when(mapper.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(summary());
        when(mapper.markDeliveredPublished(eq("TRK-AB12CD3456"), any(LocalDateTime.class)))
                .thenReturn(0);

        publisher.on(new TransportStatusUpdatedEvent(
                "TRK-AB12CD3456",
                TransportStatus.AWAITING_CLAIM, TransportStatus.DELIVERED,
                "USNYC", null, LocalDateTime.of(2026, 8, 16, 14, 0), null));

        verify(eventGateway, never()).publish(any(Object[].class));
    }

    @Test
    @DisplayName("DELIVERED 以外への遷移では発行しない")
    void DELIVERED以外では発行しない() {
        publisher.on(new TransportStatusUpdatedEvent(
                "TRK-AB12CD3456",
                TransportStatus.NOT_RECEIVED, TransportStatus.RECEIVED,
                "JPTYO", null, LocalDateTime.now(), null));

        verify(eventGateway, never()).publish(any(Object[].class));
    }

    @Test
    @DisplayName("tracking_summary が未到着の場合はスキップする")
    void summary未到着でスキップ() {
        when(mapper.findByTrackingNumber("TRK-AB12CD3456")).thenReturn(null);

        publisher.on(new TransportStatusUpdatedEvent(
                "TRK-AB12CD3456",
                TransportStatus.AWAITING_CLAIM, TransportStatus.DELIVERED,
                "USNYC", null, LocalDateTime.now(), null));

        verify(eventGateway, never()).publish(any(Object[].class));
    }
}
