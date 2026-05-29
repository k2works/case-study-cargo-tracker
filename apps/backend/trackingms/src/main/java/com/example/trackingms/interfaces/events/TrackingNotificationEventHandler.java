package com.example.trackingms.interfaces.events;

import com.example.trackingms.application.outboundservices.notification.NotificationAcl;
import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingExceptionEscalatedEvent;
import com.example.trackingms.domain.events.TrackingExceptionRegisteredEvent;
import com.example.trackingms.domain.events.TrackingExceptionResolvedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 通知トリガー用の EventHandler（US14 / US17 / IT5 0.6）。
 *
 * <p>{@link NotificationAcl} を呼び出して荷主への通知を発火する。実メール送信は
 * IT6 以降で {@code LoggingNotificationAcl} を置き換える形で実装する。</p>
 *
 * <p>本ハンドラはローカルイベントを購読するため、default プロセッサ
 * （event store source）で動作する。</p>
 */
@Component
@ProcessingGroup("tracking-notifications")
public class TrackingNotificationEventHandler {

    private final NotificationAcl notificationAcl;

    public TrackingNotificationEventHandler(NotificationAcl notificationAcl) {
        this.notificationAcl = notificationAcl;
    }

    @EventHandler
    public void on(TrackingInitializedEvent event) {
        notificationAcl.notifyTrackingIssued(event.trackingNumber(), event.bookingId());
    }

    @EventHandler
    public void on(TransportStatusUpdatedEvent event) {
        notificationAcl.notifyStatusChanged(
                event.trackingNumber(),
                event.fromStatus(),
                event.toStatus(),
                event.unlocode());
    }

    @EventHandler
    public void on(CargoMisroutedEvent event) {
        notificationAcl.notifyMisrouted(event.trackingNumber(), event.unlocode());
    }

    // --- IT6 タスク 2.5 / 3.2：US19 / US20 例外通知 ---

    @EventHandler
    public void on(TrackingExceptionRegisteredEvent event) {
        notificationAcl.notifyExceptionRegistered(
                event.trackingNumber(),
                event.exceptionId(),
                event.type().name(),
                event.occurredUnlocode(),
                event.description());
    }

    @EventHandler
    public void on(TrackingExceptionResolvedEvent event) {
        notificationAcl.notifyExceptionResolved(
                event.trackingNumber(),
                event.exceptionId(),
                event.resolution());
    }

    @EventHandler
    public void on(TrackingExceptionEscalatedEvent event) {
        notificationAcl.notifyExceptionEscalation(
                event.trackingNumber(),
                event.exceptionId(),
                event.type().name());
    }
}
