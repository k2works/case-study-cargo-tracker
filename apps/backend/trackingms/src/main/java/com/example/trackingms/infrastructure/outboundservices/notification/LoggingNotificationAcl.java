package com.example.trackingms.infrastructure.outboundservices.notification;

import com.example.trackingms.application.outboundservices.notification.NotificationAcl;
import com.example.trackingms.domain.model.TransportStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link NotificationAcl} のスタブ実装（IT5 0.6）。
 *
 * <p>実メール送信は IT6 以降で外部連携 ADR とともに実装する。本実装は
 * INFO レベルのログ出力のみを行い、通知トリガーの到達性をテストで担保できるようにする。</p>
 */
@Component
public class LoggingNotificationAcl implements NotificationAcl {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAcl.class);

    @Override
    public void notifyTrackingIssued(String trackingNumber, String bookingId) {
        log.info("[NOTIFY:TRACKING_ISSUED] trackingNumber={} bookingId={}",
                trackingNumber, bookingId);
    }

    @Override
    public void notifyStatusChanged(String trackingNumber,
                                    TransportStatus fromStatus,
                                    TransportStatus toStatus,
                                    String unlocode) {
        log.info("[NOTIFY:STATUS_CHANGED] trackingNumber={} {} -> {} unlocode={}",
                trackingNumber, fromStatus, toStatus, unlocode);
    }

    @Override
    public void notifyMisrouted(String trackingNumber, String unlocode) {
        log.warn("[NOTIFY:MISROUTED] trackingNumber={} unlocode={}",
                trackingNumber, unlocode);
    }
}
