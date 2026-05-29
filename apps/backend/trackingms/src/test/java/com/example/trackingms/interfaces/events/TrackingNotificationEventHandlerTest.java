package com.example.trackingms.interfaces.events;

import com.example.trackingms.application.outboundservices.notification.NotificationAcl;
import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingExceptionEscalatedEvent;
import com.example.trackingms.domain.events.TrackingExceptionRegisteredEvent;
import com.example.trackingms.domain.events.TrackingExceptionResolvedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.model.ExceptionType;
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

    // --- IT6 タスク 2.5 / 3.2：US19 / US20 例外通知 ---

    @Test
    @DisplayName("US19: TrackingExceptionRegisteredEvent で notifyExceptionRegistered が呼ばれる")
    void 例外登録通知() {
        TrackingExceptionRegisteredEvent event = new TrackingExceptionRegisteredEvent(
                "TRK-AB12CD3456", "EX-001",
                ExceptionType.DELAY,
                LocalDateTime.of(2026, 7, 25, 9, 0),
                "SGSIN", "悪天候のため寄港不可", false);

        handler.on(event);

        verify(acl).notifyExceptionRegistered(
                eq("TRK-AB12CD3456"),
                eq("EX-001"),
                eq("DELAY"),
                eq("SGSIN"),
                eq("悪天候のため寄港不可"));
    }

    @Test
    @DisplayName("US19: TrackingExceptionResolvedEvent で notifyExceptionResolved が呼ばれる")
    void 例外解決通知() {
        TrackingExceptionResolvedEvent event = new TrackingExceptionResolvedEvent(
                "TRK-AB12CD3456", "EX-001",
                "代替ルート手配済み",
                LocalDateTime.of(2026, 7, 26, 14, 0));

        handler.on(event);

        verify(acl).notifyExceptionResolved(
                eq("TRK-AB12CD3456"),
                eq("EX-001"),
                eq("代替ルート手配済み"));
    }

    @Test
    @DisplayName("US20: TrackingExceptionEscalatedEvent で notifyExceptionEscalation が呼ばれる")
    void 例外escalation通知() {
        TrackingExceptionEscalatedEvent event = new TrackingExceptionEscalatedEvent(
                "TRK-AB12CD3456", "EX-002",
                ExceptionType.LOSS,
                LocalDateTime.of(2026, 7, 26, 14, 0));

        handler.on(event);

        verify(acl).notifyExceptionEscalation(
                eq("TRK-AB12CD3456"),
                eq("EX-002"),
                eq("LOSS"));
    }
}
