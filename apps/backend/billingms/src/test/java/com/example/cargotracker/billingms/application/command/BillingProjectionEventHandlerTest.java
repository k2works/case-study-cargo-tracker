package com.example.cargotracker.billingms.application.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.billingms.domain.model.events.ChargeCalculatedEvent;
import com.example.cargotracker.billingms.domain.model.events.InvoiceCreatedEvent;
import com.example.cargotracker.billingms.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billingms.domain.model.events.PaymentRecordedEvent;
import com.example.cargotracker.billingms.infrastructure.persistence.InvoiceMapper;
import com.example.cargotracker.billingms.infrastructure.persistence.PaymentMapper;
import com.example.cargotracker.billingms.infrastructure.persistence.PaymentRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BillingProjectionEventHandler 単体テスト")
class BillingProjectionEventHandlerTest {

    private InvoiceMapper invoiceMapper;
    private PaymentMapper paymentMapper;
    private BillingProjectionEventHandler handler;

    @BeforeEach
    void setUp() {
        invoiceMapper = mock(InvoiceMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        handler = new BillingProjectionEventHandler(invoiceMapper, paymentMapper);
    }

    @Test
    @DisplayName("InvoiceCreatedEvent で invoiceMapper.insert が呼ばれる")
    void on_InvoiceCreatedEvent() {
        var event = new InvoiceCreatedEvent("INV-001", "B-2026-0001", "SHP-001");
        handler.on(event);
        verify(invoiceMapper).insert(any());
    }

    @Test
    @DisplayName("ChargeCalculatedEvent で invoiceMapper.updateCharge が呼ばれる")
    void on_ChargeCalculatedEvent() {
        var event = new ChargeCalculatedEvent(
                "INV-001",
                new BigDecimal("100000"),
                new BigDecimal("10000"),
                new BigDecimal("90000"),
                "JPY",
                "admin-001");
        handler.on(event);
        verify(invoiceMapper).updateCharge(
                "INV-001",
                new BigDecimal("100000"),
                new BigDecimal("10000"),
                new BigDecimal("90000"),
                "CALCULATED");
    }

    @Test
    @DisplayName("InvoiceIssuedEvent で paymentMapper.updateInvoiceIssued が呼ばれる")
    void on_InvoiceIssuedEvent() {
        var due = LocalDate.of(2026, 9, 30);
        var event = new InvoiceIssuedEvent("INV-001", "INV-20260901-000001", due, "admin-001");
        handler.on(event);
        verify(paymentMapper).updateInvoiceIssued(
                "INV-001",
                "INV-20260901-000001",
                due,
                "INVOICED");
    }

    @Test
    @DisplayName("PaymentRecordedEvent で paymentMapper.insert と updateInvoicePaid が呼ばれる")
    void on_PaymentRecordedEvent() {
        var now = LocalDateTime.of(2026, 9, 15, 10, 0);
        var event = new PaymentRecordedEvent("INV-001", new BigDecimal("100000"), "BANK_TRANSFER", now, "admin-001");
        handler.on(event);
        verify(paymentMapper).insert(any(PaymentRecord.class));
        verify(paymentMapper).updateInvoicePaid("INV-001", "PAID");
    }
}
