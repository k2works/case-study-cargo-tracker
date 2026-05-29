package com.example.trackingms.interfaces.events;

import com.example.trackingms.application.outboundservices.notification.NotificationAcl;
import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.model.TransportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link TrackingNotificationEventHandler} のユニットテスト（IT5 0.6）。
 *
 * <p>NotificationAcl がイベントごとに適切なメソッドで呼び出されることを検証する。</p>
 */
class TrackingNotificationEventHandlerTest {

    private NotificationAcl acl;
    private TrackingNotificationEventHandler handler;

    @BeforeEach
    void setUp() {
        acl = mock(NotificationAcl.class);
        handler = new TrackingNotificationEventHandler(acl);
    }

    @Test
    @DisplayName("US14: TrackingInitializedEvent で notifyTrackingIssued が呼ばれる")
    void 採番通知() {
        handler.on(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"));

        verify(acl).notifyTrackingIssued(eq("TRK-AB12CD3456"), eq("B-001"));
    }

    @Test
    @DisplayName("US17: TransportStatusUpdatedEvent で notifyStatusChanged が呼ばれる")
    void 状態変更通知() {
        TransportStatusUpdatedEvent event = new TransportStatusUpdatedEvent(
                "TRK-AB12CD3456",
                TransportStatus.NOT_RECEIVED, TransportStatus.RECEIVED,
                "JPTYO", null, LocalDateTime.of(2026, 7, 20, 10, 0), "受領");

        handler.on(event);

        verify(acl).notifyStatusChanged(
                eq("TRK-AB12CD3456"),
                eq(TransportStatus.NOT_RECEIVED),
                eq(TransportStatus.RECEIVED),
                eq("JPTYO"));
    }

    @Test
    @DisplayName("US17: CargoMisroutedEvent で notifyMisrouted が呼ばれる")
    void 誤配送通知() {
        handler.on(new CargoMisroutedEvent("TRK-AB12CD3456", "CNHKG",
                LocalDateTime.of(2026, 7, 22, 12, 0)));

        verify(acl).notifyMisrouted(eq("TRK-AB12CD3456"), eq("CNHKG"));
    }
}
