package com.example.billingms.interfaces.events;

import com.example.billingms.application.outboundservices.notification.NotificationAcl;
import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.shared.events.PaymentRecordedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link InvoiceNotificationEventHandler} の単体テスト（US23、IT7 T4.4）。
 *
 * <p>3 Event 各々で {@link NotificationAcl} の対応メソッドが正しいパラメータで呼ばれることを検証する。</p>
 */
class InvoiceNotificationEventHandlerTest {

    private NotificationAcl acl;
    private InvoiceNotificationEventHandler handler;

    @BeforeEach
    void setUp() {
        acl = mock(NotificationAcl.class);
        handler = new InvoiceNotificationEventHandler(acl);
    }

    @Test
    @DisplayName("US23: InvoiceIssuedEvent → notifyInvoiceIssued")
    void issued() {
        InvoiceIssuedEvent event = new InvoiceIssuedEvent(
                "INV-001", "S-001", "INV-20260901-0001",
                LocalDate.of(2026, 10, 1),
                new BigDecimal("330000"),
                LocalDateTime.now()
        );

        handler.on(event);

        verify(acl).notifyInvoiceIssued("INV-001", "S-001", "INV-20260901-0001",
                LocalDate.of(2026, 10, 1), new BigDecimal("330000"));
    }

    @Test
    @DisplayName("US23: PaymentRecordedEvent → notifyPaymentReceived")
    void paid() {
        PaymentRecordedEvent event = new PaymentRecordedEvent(
                "INV-001", "PAY-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY",
                LocalDateTime.now(), LocalDateTime.now()
        );

        handler.on(event);

        verify(acl).notifyPaymentReceived("INV-001", "S-001", "PAY-001", new BigDecimal("330000"));
    }

    @Test
    @DisplayName("US23: InvoiceOverdueEvent → notifyOverdue")
    void overdue() {
        InvoiceOverdueEvent event = new InvoiceOverdueEvent(
                "INV-001", "S-001", LocalDateTime.now()
        );

        handler.on(event);

        verify(acl).notifyOverdue("INV-001", "S-001");
    }
}
