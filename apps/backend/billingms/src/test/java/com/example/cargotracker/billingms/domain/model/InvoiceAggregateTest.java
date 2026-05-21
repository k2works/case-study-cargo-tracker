package com.example.cargotracker.billingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.cargotracker.billingms.domain.model.aggregates.Invoice;
import com.example.cargotracker.billingms.domain.model.commands.CalculateChargeCommand;
import com.example.cargotracker.billingms.domain.model.commands.CreateInvoiceCommand;
import com.example.cargotracker.billingms.domain.model.commands.IssueInvoiceCommand;
import com.example.cargotracker.billingms.domain.model.commands.RecordPaymentCommand;
import com.example.cargotracker.billingms.domain.model.events.ChargeCalculatedEvent;
import com.example.cargotracker.billingms.domain.model.events.InvoiceCreatedEvent;
import com.example.cargotracker.billingms.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billingms.domain.model.events.PaymentRecordedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Invoice 集約のユニットテスト。
 *
 * <p>{@link EventAppender} をモック化し、CommandHandler と {@code @EventSourcingHandler} を直接検証する。</p>
 */
@DisplayName("Invoice 集約テスト")
class InvoiceAggregateTest {

    @Test
    @DisplayName("CreateInvoiceCommand で InvoiceCreatedEvent が発行される")
    void createInvoice_InvoiceCreatedEventが発行される() {
        EventAppender appender = mock(EventAppender.class);
        var command = new CreateInvoiceCommand("INV-001", "B-2026-0001", "SHP-001");

        String returned = Invoice.create(command, appender);

        assertThat(returned).isEqualTo("INV-001");

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InvoiceCreatedEvent.class);
        var event = (InvoiceCreatedEvent) captor.getValue();
        assertThat(event.invoiceId()).isEqualTo("INV-001");
        assertThat(event.bookingId()).isEqualTo("B-2026-0001");
        assertThat(event.shipperId()).isEqualTo("SHP-001");
    }

    @Test
    @DisplayName("CalculateChargeCommand で割引後金額が計算されイベントが発行される")
    void calculateCharge_割引後金額が計算される() {
        EventAppender appender = mock(EventAppender.class);
        var invoice = invoicePending("INV-001");

        invoice.calculateCharge(new CalculateChargeCommand(
                "INV-001",
                new BigDecimal("100000"),
                "JPY",
                new BigDecimal("0.10"),
                "admin-001"), appender);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ChargeCalculatedEvent.class);
        var event = (ChargeCalculatedEvent) captor.getValue();
        assertThat(event.baseAmount()).isEqualByComparingTo("100000");
        assertThat(event.discountRate()).isEqualByComparingTo("0.10");
        assertThat(event.finalAmount()).isEqualByComparingTo("90000");
        assertThat(event.operatorId()).isEqualTo("admin-001");
    }

    @Test
    @DisplayName("割引率 0 の場合は基本料金がそのまま確定額になる")
    void calculateCharge_割引率0で基本料金がそのまま() {
        EventAppender appender = mock(EventAppender.class);
        var invoice = invoicePending("INV-001");

        invoice.calculateCharge(new CalculateChargeCommand(
                "INV-001",
                new BigDecimal("115000"),
                "JPY",
                BigDecimal.ZERO,
                "admin-001"), appender);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        var event = (ChargeCalculatedEvent) captor.getValue();
        assertThat(event.finalAmount()).isEqualByComparingTo("115000");
    }

    @Test
    @DisplayName("割引率が 0.30 を超える場合は IllegalArgumentException が投げられる")
    void calculateCharge_割引率超過でエラー() {
        EventAppender appender = mock(EventAppender.class);
        var invoice = invoicePending("INV-001");
        var command = new CalculateChargeCommand("INV-001", new BigDecimal("100000"), "JPY", new BigDecimal("0.31"), "admin-001");

        assertThatThrownBy(() -> invoice.calculateCharge(command, appender))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30%");
    }

    @Test
    @DisplayName("CALCULATED 状態の Invoice にさらに CalculateChargeCommand を送ると IllegalStateException")
    void calculateCharge_CALCULATED状態に再算出はエラー() {
        EventAppender appender = mock(EventAppender.class);
        var invoice = invoiceCalculated("INV-001");
        var command = new CalculateChargeCommand("INV-001", new BigDecimal("200000"), "JPY", BigDecimal.ZERO, "admin-001");

        assertThatThrownBy(() -> invoice.calculateCharge(command, appender))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("IssueInvoiceCommand で CALCULATED → INVOICED に遷移しイベントが発行される")
    void issueInvoice_INVOICED状態に遷移() {
        EventAppender appender = mock(EventAppender.class);
        var invoice = invoiceCalculated("INV-001");
        var due = LocalDate.of(2026, 9, 30);

        invoice.issueInvoice(new IssueInvoiceCommand("INV-001", due, "admin-001"), appender);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(InvoiceIssuedEvent.class);
        var event = (InvoiceIssuedEvent) captor.getValue();
        assertThat(event.invoiceId()).isEqualTo("INV-001");
        assertThat(event.paymentDue()).isEqualTo(due);
        assertThat(event.operatorId()).isEqualTo("admin-001");
        assertThat(event.invoiceNumber()).isNotBlank();
    }

    @Test
    @DisplayName("PENDING 状態で IssueInvoiceCommand は IllegalStateException")
    void issueInvoice_PENDING状態はエラー() {
        EventAppender appender = mock(EventAppender.class);
        var invoice = invoicePending("INV-001");
        var command = new IssueInvoiceCommand("INV-001", LocalDate.of(2026, 9, 30), "admin-001");

        assertThatThrownBy(() -> invoice.issueInvoice(command, appender))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CALCULATED");
    }

    @Test
    @DisplayName("RecordPaymentCommand で INVOICED → PAID に遷移しイベントが発行される")
    void recordPayment_PAID状態に遷移() {
        EventAppender appender = mock(EventAppender.class);
        var invoice = invoiceIssued("INV-001");

        invoice.recordPayment(new RecordPaymentCommand(
                "INV-001", new BigDecimal("100000"), "BANK_TRANSFER", "admin-001"), appender);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(PaymentRecordedEvent.class);
        var event = (PaymentRecordedEvent) captor.getValue();
        assertThat(event.invoiceId()).isEqualTo("INV-001");
        assertThat(event.paidAmount()).isEqualByComparingTo("100000");
        assertThat(event.paymentMethod()).isEqualTo("BANK_TRANSFER");
    }

    @Test
    @DisplayName("CALCULATED 状態で RecordPaymentCommand は IllegalStateException")
    void recordPayment_CALCULATED状態はエラー() {
        EventAppender appender = mock(EventAppender.class);
        var invoice = invoiceCalculated("INV-001");
        var command = new RecordPaymentCommand("INV-001", new BigDecimal("100000"), "BANK_TRANSFER", "admin-001");

        assertThatThrownBy(() -> invoice.recordPayment(command, appender))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVOICED");
    }

    // --- ヘルパー ---

    private static Invoice invoicePending(String invoiceId) {
        var invoice = new Invoice();
        invoice.on(new InvoiceCreatedEvent(invoiceId, "B-2026-0001", "SHP-001"));
        return invoice;
    }

    private static Invoice invoiceCalculated(String invoiceId) {
        var invoice = invoicePending(invoiceId);
        invoice.on(new ChargeCalculatedEvent(
                invoiceId,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                "JPY",
                "admin-001"));
        return invoice;
    }

    private static Invoice invoiceIssued(String invoiceId) {
        var invoice = invoiceCalculated(invoiceId);
        invoice.on(new InvoiceIssuedEvent(
                invoiceId, "INV-20260901-000001", LocalDate.of(2026, 9, 30), "admin-001"));
        return invoice;
    }
}
