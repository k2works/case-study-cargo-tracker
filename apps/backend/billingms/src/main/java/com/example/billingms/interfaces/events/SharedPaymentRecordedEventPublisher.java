package com.example.billingms.interfaces.events;

import com.example.billingms.domain.events.PaymentRecordedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * billingms 内部 {@link PaymentRecordedEvent} を shared/events の
 * {@link com.example.shared.events.PaymentRecordedEvent} に変換して cross-service へ発行する
 * outbound EventHandler（US23、IT7 T4.5）。
 *
 * <p>{@code @ProcessingGroup("outbound-billing-cross")} は ADR-0014/0016 命名規約の
 * {@code outbound-} prefix。bookingms 側 {@code CrossBillingPaymentHandler} がこの
 * shared event を購読し、{@code MarkBookingSettledCommand} を発火する。</p>
 *
 * <p>集約発火型の純粋性（ADR-0012）を保つため、内部 event は集約から発火、cross-service event は
 * 本 publisher 経由で派生発行する設計（shared event の payload 変更が集約に直接波及しない）。</p>
 */
@Component
@ProcessingGroup("outbound-billing-cross")
public class SharedPaymentRecordedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SharedPaymentRecordedEventPublisher.class);

    private final EventGateway eventGateway;

    public SharedPaymentRecordedEventPublisher(EventGateway eventGateway) {
        this.eventGateway = eventGateway;
    }

    @EventHandler
    public void on(PaymentRecordedEvent event) {
        com.example.shared.events.PaymentRecordedEvent shared =
                new com.example.shared.events.PaymentRecordedEvent(
                        event.invoiceId(),
                        event.paymentId(),
                        event.bookingId(),
                        event.shipperId(),
                        event.paidAmount(),
                        event.currency(),
                        event.paidAt(),
                        event.recordedAt()
                );
        eventGateway.publish(shared);
        log.info("[outbound-billing-cross] shared PaymentRecordedEvent 発行 invoiceId={} bookingId={}",
                event.invoiceId(), event.bookingId());
    }
}
