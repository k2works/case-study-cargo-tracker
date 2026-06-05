package com.example.billingms.application.projections;

import com.example.billingms.domain.events.DiscountAppliedEvent;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
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

import static org.mockito.Mockito.*;

/**
 * {@link InvoiceProjection} の Mapper 呼び出し検証テスト（IT7 review M1 リファクタ後）。
 *
 * <p>InvoiceProjection は EventHandler から委譲される application service で、Mapper への
 * 書込みを集約する。状態遷移ごとに updateForXxx が増えた場合の影響範囲が本テストに閉じ込められる。</p>
 */
class InvoiceProjectionTest {

    private InvoiceSummaryMapper summaryMapper;
    private InvoiceLineMapper lineMapper;
    private PaymentMapper paymentMapper;
    private InvoiceProjection projection;

    @BeforeEach
    void setUp() {
        summaryMapper = mock(InvoiceSummaryMapper.class);
        lineMapper = mock(InvoiceLineMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        projection = new InvoiceProjection(summaryMapper, lineMapper, paymentMapper);
    }

    @Test
    @DisplayName("US21: InvoiceCalculatedEvent → insertInvoice + BASIC 行 INSERT")
    void calculated() {
        InvoiceCalculatedEvent event = new InvoiceCalculatedEvent(
                "INV-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY", LocalDateTime.now());

        projection.apply(event);

        verify(summaryMapper).insertInvoice(
                "INV-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY", "CALCULATED");
        verify(lineMapper).insertInvoiceLine(
                eq("INV-001"), eq(1), eq("BASIC"),
                anyString(), eq(new BigDecimal("330000")), isNull());
    }

    @Test
    @DisplayName("US22: DiscountAppliedEvent → updateDiscount + DISCOUNT 行 INSERT（負値）")
    void discount() {
        DiscountAppliedEvent event = new DiscountAppliedEvent(
                "INV-001", "S-001",
                new BigDecimal("0.15"), new BigDecimal("49500"),
                new BigDecimal("280500"), LocalDateTime.now());
        when(lineMapper.findMaxLineSeq("INV-001")).thenReturn(1);

        projection.apply(event);

        verify(summaryMapper).updateDiscount(
                "INV-001", new BigDecimal("49500"), new BigDecimal("280500"));
        verify(lineMapper).insertInvoiceLine(
                eq("INV-001"), eq(2), eq("DISCOUNT"),
                contains("15"), eq(new BigDecimal("-49500")), eq("CORPORATE"));
    }

    @Test
    @DisplayName("US23: InvoiceIssuedEvent → updateForIssued 呼出")
    void issued() {
        LocalDate due = LocalDate.of(2026, 9, 19);
        InvoiceIssuedEvent event = new InvoiceIssuedEvent(
                "INV-001", "S-001", "INV-20260820-0001",
                due, new BigDecimal("330000"), LocalDateTime.now());

        projection.apply(event);

        verify(summaryMapper).updateForIssued("INV-001", "INV-20260820-0001", due);
    }

    @Test
    @DisplayName("US23: PaymentRecordedEvent → insertPayment + updateForPaid 呼出（method/ref は null）")
    void payment() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 22, 15, 0);
        PaymentRecordedEvent event = new PaymentRecordedEvent(
                "INV-001", "PAY-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY",
                paidAt, LocalDateTime.now());

        projection.apply(event);

        verify(paymentMapper).insertPayment(
                "PAY-001", "INV-001",
                new BigDecimal("330000"), "JPY",
                paidAt, null, null);
        verify(summaryMapper).updateForPaid("INV-001", paidAt);
    }

    @Test
    @DisplayName("US23: InvoiceOverdueEvent → updateForOverdue 呼出")
    void overdue() {
        InvoiceOverdueEvent event = new InvoiceOverdueEvent(
                "INV-001", "S-001", LocalDateTime.now());

        projection.apply(event);

        verify(summaryMapper).updateForOverdue("INV-001");
    }
}
