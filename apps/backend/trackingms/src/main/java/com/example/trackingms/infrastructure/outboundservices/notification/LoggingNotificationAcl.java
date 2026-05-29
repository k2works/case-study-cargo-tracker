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

    @Override
    public void notifyExceptionRegistered(String trackingNumber,
                                          String exceptionId,
                                          String exceptionType,
                                          String occurredUnlocode,
                                          String description) {
        log.info("[NOTIFY:EXCEPTION_REGISTERED] trackingNumber={} exceptionId={} type={} unlocode={} description={}",
                trackingNumber, exceptionId, exceptionType, occurredUnlocode, description);
    }

    @Override
    public void notifyExceptionResolved(String trackingNumber,
                                        String exceptionId,
                                        String resolution) {
        log.info("[NOTIFY:EXCEPTION_RESOLVED] trackingNumber={} exceptionId={} resolution={}",
                trackingNumber, exceptionId, resolution);
    }

    @Override
    public void notifyExceptionEscalation(String trackingNumber,
                                          String exceptionId,
                                          String exceptionType) {
        // US20 受入基準 3：管理職への escalation。実メール送信は IT8 で切替予定（ADR-0015）
        log.warn("[NOTIFY:EXCEPTION_ESCALATION] trackingNumber={} exceptionId={} type={}",
                trackingNumber, exceptionId, exceptionType);
    }
}
