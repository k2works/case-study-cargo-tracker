package com.example.billingms.infrastructure.outboundservices.notification;

import com.example.billingms.application.outboundservices.notification.NotificationAcl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@link NotificationAcl} のスタブ実装（US23、IT7 T4.4）。
 *
 * <p>実メール送信は IT8 で SendGrid 統合（ADR-0018）に切替予定。本実装は INFO/WARN
 * レベルのログ出力のみを行い、通知トリガーの到達性をテストで担保できるようにする。</p>
 */
@Component
public class LoggingNotificationAcl implements NotificationAcl {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAcl.class);

    @Override
    public void notifyInvoiceIssued(String invoiceId,
                                    String shipperId,
                                    String invoiceNumber,
                                    LocalDate paymentDue,
                                    BigDecimal totalAmount) {
        log.info("[NOTIFY:INVOICE_ISSUED] invoiceId={} shipperId={} invoiceNumber={} paymentDue={} totalAmount={}",
                invoiceId, shipperId, invoiceNumber, paymentDue, totalAmount);
    }

    @Override
    public void notifyPaymentReceived(String invoiceId,
                                      String shipperId,
                                      String paymentId,
                                      BigDecimal paidAmount) {
        log.info("[NOTIFY:PAYMENT_RECEIVED] invoiceId={} shipperId={} paymentId={} paidAmount={}",
                invoiceId, shipperId, paymentId, paidAmount);
    }

    @Override
    public void notifyOverdue(String invoiceId, String shipperId) {
        // 督促は警告レベル。経理担当者への督促通知も IT8 で escalation 統合
        log.warn("[NOTIFY:OVERDUE] invoiceId={} shipperId={}", invoiceId, shipperId);
    }
}
