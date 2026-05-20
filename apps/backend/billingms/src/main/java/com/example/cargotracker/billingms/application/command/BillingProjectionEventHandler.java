package com.example.cargotracker.billingms.application.command;

import com.example.cargotracker.billingms.domain.model.events.ChargeCalculatedEvent;
import com.example.cargotracker.billingms.domain.model.events.InvoiceCreatedEvent;
import com.example.cargotracker.billingms.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billingms.domain.model.events.PaymentRecordedEvent;
import com.example.cargotracker.billingms.infrastructure.persistence.InvoiceMapper;
import com.example.cargotracker.billingms.infrastructure.persistence.InvoiceRecord;
import com.example.cargotracker.billingms.infrastructure.persistence.PaymentMapper;
import com.example.cargotracker.billingms.infrastructure.persistence.PaymentRecord;
import java.math.BigDecimal;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 請求イベントの Read Model 更新ハンドラー（CQRS Query Side）。
 *
 * <p>Invoice 集約から発行されるイベントを購読して invoice テーブルを更新する。</p>
 */
@Component
@Profile("!springboot-integration-test")
public class BillingProjectionEventHandler {

    private final InvoiceMapper invoiceMapper;
    private final PaymentMapper paymentMapper;

    public BillingProjectionEventHandler(InvoiceMapper invoiceMapper, PaymentMapper paymentMapper) {
        this.invoiceMapper = invoiceMapper;
        this.paymentMapper = paymentMapper;
    }

    @EventSourcingHandler
    @Transactional
    public void on(InvoiceCreatedEvent event) {
        var record = new InvoiceRecord(
                event.invoiceId(),
                event.bookingId(),
                event.shipperId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "JPY",
                "PENDING",
                null, null, null, null, null, null, 0L);
        invoiceMapper.insert(record);
    }

    @EventSourcingHandler
    @Transactional
    public void on(ChargeCalculatedEvent event) {
        invoiceMapper.updateCharge(
                event.invoiceId(),
                event.baseAmount(),
                event.baseAmount().subtract(event.finalAmount()),
                event.finalAmount(),
                "CALCULATED");
    }

    @EventSourcingHandler
    @Transactional
    public void on(InvoiceIssuedEvent event) {
        paymentMapper.updateInvoiceIssued(
                event.invoiceId(),
                event.invoiceNumber(),
                event.paymentDue(),
                "INVOICED");
    }

    @EventSourcingHandler
    @Transactional
    public void on(PaymentRecordedEvent event) {
        var payment = new PaymentRecord(
                UUID.randomUUID().toString(),
                event.invoiceId(),
                event.paidAmount(),
                "JPY",
                event.paidAt(),
                event.paymentMethod(),
                null);
        paymentMapper.insert(payment);
        paymentMapper.updateInvoicePaid(event.invoiceId(), "PAID");
    }
}
