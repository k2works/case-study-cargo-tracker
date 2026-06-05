package com.example.billingms.interfaces.events;

import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceLineMapper;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import com.example.billingms.infrastructure.repositories.mybatis.PaymentMapper;
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
 * {@link InvoiceProjectionsEventHandler} の US23 拡張投影テスト（IT7 T4.8）。
 *
 * <p>InvoiceIssuedEvent / PaymentRecordedEvent / InvoiceOverdueEvent 受信時の Mapper 呼び出しを検証する。</p>
 */
class InvoiceProjectionsEventHandlerTest {

    private InvoiceSummaryMapper summaryMapper;
    private InvoiceLineMapper lineMapper;
    private PaymentMapper paymentMapper;
    private InvoiceProjectionsEventHandler handler;

    @BeforeEach
    void setUp() {
        summaryMapper = mock(InvoiceSummaryMapper.class);
        lineMapper = mock(InvoiceLineMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        handler = new InvoiceProjectionsEventHandler(summaryMapper, lineMapper, paymentMapper);
    }

    @Test
    @DisplayName("US23 T4.3: InvoiceIssuedEvent → updateForIssued 呼出")
    void issued投影() {
        LocalDate due = LocalDate.of(2026, 9, 19);
        InvoiceIssuedEvent event = new InvoiceIssuedEvent(
                "INV-001", "S-001", "INV-20260820-0001",
                due, new BigDecimal("330000"), LocalDateTime.now());

        handler.on(event);

        verify(summaryMapper).updateForIssued("INV-001", "INV-20260820-0001", due);
    }

    @Test
    @DisplayName("US23 T4.3: PaymentRecordedEvent → insertPayment + updateForPaid 呼出")
    void payment投影() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 22, 15, 0);
        // shared event は paymentMethod / externalReference を含まない（H1 修正後の集約発火型）
        PaymentRecordedEvent event = new PaymentRecordedEvent(
                "INV-001", "PAY-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY",
                paidAt, LocalDateTime.now());

        handler.on(event);

        verify(paymentMapper).insertPayment(
                "PAY-001", "INV-001",
                new BigDecimal("330000"), "JPY",
                paidAt, null, null);
        verify(summaryMapper).updateForPaid("INV-001", paidAt);
    }

    @Test
    @DisplayName("US23 T4.3: InvoiceOverdueEvent → updateForOverdue 呼出")
    void overdue投影() {
        InvoiceOverdueEvent event = new InvoiceOverdueEvent(
                "INV-001", "S-001", LocalDateTime.now());

        handler.on(event);

        verify(summaryMapper).updateForOverdue("INV-001");
    }
}
