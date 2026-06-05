package com.example.billingms.interfaces.events;

import com.example.billingms.application.projections.InvoiceProjection;
import com.example.billingms.domain.events.DiscountAppliedEvent;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.billingms.domain.events.PaymentDetailRecorded;
import com.example.shared.events.PaymentRecordedEvent;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Invoice 集約イベントの Read Model 投影 EventHandler（US21 / US22 / US23）。
 *
 * <p>{@code @ProcessingGroup("local-billing")} は ADR-0014/0016 命名規約準拠（local- prefix）で
 * subscribing モード。Mapper 直叩きは {@link InvoiceProjection} 集約クラスに委譲し、本クラスは
 * Axon EventHandler → application service ディスパッチに専念する（IT7 review M1 対応）。</p>
 *
 * <p>新規イベント追加時は {@link InvoiceProjection} に {@code apply(event)} メソッドを 1 つ追加し、
 * 本クラスに 1 つ {@code @EventHandler} を追加するだけで完結する（OCP）。</p>
 */
@Component
@ProcessingGroup("local-billing")
public class InvoiceProjectionsEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProjectionsEventHandler.class);

    private final InvoiceProjection projection;

    public InvoiceProjectionsEventHandler(InvoiceProjection projection) {
        this.projection = projection;
    }

    @EventHandler
    public void on(InvoiceCalculatedEvent event) {
        projection.apply(event);
        log.info("[local-billing] Invoice 投影 invoiceId={} bookingId={} basicAmount={}",
                event.invoiceId(), event.bookingId(), event.basicAmount());
    }

    @EventHandler
    public void on(DiscountAppliedEvent event) {
        projection.apply(event);
        log.info("[local-billing] Discount 投影 invoiceId={} discountAmount={} totalAmount={}",
                event.invoiceId(), event.discountAmount(), event.totalAmount());
    }

    @EventHandler
    public void on(InvoiceIssuedEvent event) {
        projection.apply(event);
        log.info("[local-billing] Invoice 発行投影 invoiceId={} invoiceNumber={} paymentDue={}",
                event.invoiceId(), event.invoiceNumber(), event.paymentDue());
    }

    @EventHandler
    public void on(PaymentRecordedEvent event) {
        projection.apply(event);
        log.info("[local-billing] Payment 投影 invoiceId={} paymentId={} paidAmount={}",
                event.invoiceId(), event.paymentId(), event.paidAmount());
    }

    @EventHandler
    public void on(InvoiceOverdueEvent event) {
        projection.apply(event);
        log.info("[local-billing] Invoice 督促投影 invoiceId={} markedAt={}",
                event.invoiceId(), event.markedAt());
    }

    /**
     * IT8 T5.1 / ADR-0019: 内部 PaymentDetailRecorded を受信して
     * payment_method / external_reference を補完 UPDATE する。
     */
    @EventHandler
    public void on(PaymentDetailRecorded event) {
        projection.apply(event);
        log.info("[local-billing] PaymentDetail 投影 invoiceId={} paymentId={} method={} ref={}",
                event.invoiceId(), event.paymentId(),
                event.paymentMethod(), event.externalReference());
    }
}
