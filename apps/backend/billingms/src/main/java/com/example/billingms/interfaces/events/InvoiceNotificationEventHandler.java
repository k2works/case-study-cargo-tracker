package com.example.billingms.interfaces.events;

import com.example.billingms.application.outboundservices.notification.NotificationAcl;
import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.billingms.domain.events.PaymentRecordedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * Invoice 集約イベントを購読して荷主・経理担当者に通知する EventHandler（US23、IT7 T4.4）。
 *
 * <p>{@code @ProcessingGroup("outbound-billing-notification")} は ADR-0014/0016 命名規約準拠の
 * {@code outbound-} prefix。Read Model 投影とは別グループで非同期処理し、通知の遅延が投影に
 * 影響しないよう分離する。</p>
 *
 * <p>IT7 は LoggingNotificationAcl のスタブ実装。IT8 で SendGrid 統合（ADR-0018）に切替予定。</p>
 */
@Component
@ProcessingGroup("outbound-billing-notification")
public class InvoiceNotificationEventHandler {

    private final NotificationAcl notificationAcl;

    public InvoiceNotificationEventHandler(NotificationAcl notificationAcl) {
        this.notificationAcl = notificationAcl;
    }

    /** 精算書発行通知（US23 受入基準 2）。 */
    @EventHandler
    public void on(InvoiceIssuedEvent event) {
        notificationAcl.notifyInvoiceIssued(
                event.invoiceId(),
                event.shipperId(),
                event.invoiceNumber(),
                event.paymentDue(),
                event.totalAmount()
        );
    }

    /** 入金記録通知（US23 受入基準 4）。 */
    @EventHandler
    public void on(PaymentRecordedEvent event) {
        notificationAcl.notifyPaymentReceived(
                event.invoiceId(),
                event.shipperId(),
                event.paymentId(),
                event.paidAmount()
        );
    }

    /** 督促通知（US23 受入基準 5）。 */
    @EventHandler
    public void on(InvoiceOverdueEvent event) {
        notificationAcl.notifyOverdue(event.invoiceId(), event.shipperId());
    }
}
