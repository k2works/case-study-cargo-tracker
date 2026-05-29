package com.example.trackingms.interfaces.events;

import com.example.trackingms.application.outboundservices.notification.NotificationAcl;
import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
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
}
