package com.example.billingms.interfaces.events;

import com.example.billingms.domain.events.DiscountAppliedEvent;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.billingms.domain.events.PaymentRecordedEvent;
import com.example.billingms.domain.model.BillingStatus;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceLineMapper;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import com.example.billingms.infrastructure.repositories.mybatis.PaymentMapper;

import java.math.BigDecimal;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * billingms 内部の Invoice 投影 EventHandler（US21 / IT7 タスク 2.5）。
 *
 * <p>Invoice 集約のイベント（InvoiceCalculatedEvent / DiscountAppliedEvent / etc.）を受信して
 * {@code invoice} / {@code invoice_line} テーブルに投影する。{@code @ProcessingGroup("local-billing")}
 * は ADR-0014/0016 命名規約準拠（local- prefix）で subscribing モード。</p>
 *
 * <p>本 EventHandler は IT7 タスク 2.5 で InvoiceCalculatedEvent → invoice 行 + BASIC 行のみ実装。
 * DiscountAppliedEvent → DISCOUNT 行は Task 3.x、InvoiceAdjustedEvent → ADJUSTMENT 行は Task 4.x で
 * 順次追加する。</p>
 */
@Component
@ProcessingGroup("local-billing")
public class InvoiceProjectionsEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProjectionsEventHandler.class);

    private final InvoiceSummaryMapper summaryMapper;
    private final InvoiceLineMapper lineMapper;
    private final PaymentMapper paymentMapper;

    public InvoiceProjectionsEventHandler(InvoiceSummaryMapper summaryMapper,
                                          InvoiceLineMapper lineMapper,
                                          PaymentMapper paymentMapper) {
        this.summaryMapper = summaryMapper;
        this.lineMapper = lineMapper;
        this.paymentMapper = paymentMapper;
    }

    @EventHandler
    public void on(InvoiceCalculatedEvent event) {
        summaryMapper.insertInvoice(
                event.invoiceId(),
                event.bookingId(),
                event.shipperId(),
                event.basicAmount(),
                event.currency(),
                BillingStatus.CALCULATED.name()
        );
        lineMapper.insertInvoiceLine(
                event.invoiceId(),
                1,
                "BASIC",
                "基本料金（重量 × 距離 × 種別係数 + 取扱費）",
                event.basicAmount(),
                null
        );
        log.info("[local-billing] Invoice 投影 invoiceId={} bookingId={} basicAmount={}",
                event.invoiceId(), event.bookingId(), event.basicAmount());
    }

    @EventHandler
    public void on(DiscountAppliedEvent event) {
        summaryMapper.updateDiscount(
                event.invoiceId(),
                event.discountAmount(),
                event.totalAmount()
        );
        // 既存の DISCOUNT 行が無い前提で seq=2 として挿入（複数回適用は IT8 で考慮）
        Integer maxSeq = lineMapper.findMaxLineSeq(event.invoiceId());
        int nextSeq = (maxSeq == null ? 1 : maxSeq + 1);
        BigDecimal lineAmount = event.discountAmount().negate(); // DISCOUNT 行は負値
        String description = String.format(
                "法人割引（%.0f%%）", event.discountRate().multiply(new BigDecimal("100")).doubleValue());
        lineMapper.insertInvoiceLine(
                event.invoiceId(),
                nextSeq,
                "DISCOUNT",
                description,
                lineAmount,
                "CORPORATE"
        );
        log.info("[local-billing] Discount 投影 invoiceId={} discountAmount={} totalAmount={}",
                event.invoiceId(), event.discountAmount(), event.totalAmount());
    }

    /** 精算書発行投影（US23 / T4.3、InvoiceIssuedEvent → invoice_number + payment_due + INVOICED）。 */
    @EventHandler
    public void on(InvoiceIssuedEvent event) {
        summaryMapper.updateForIssued(
                event.invoiceId(),
                event.invoiceNumber(),
                event.paymentDue()
        );
        log.info("[local-billing] Invoice 発行投影 invoiceId={} invoiceNumber={} paymentDue={}",
                event.invoiceId(), event.invoiceNumber(), event.paymentDue());
    }

    /** 入金記録投影（US23 / T4.3、PaymentRecordedEvent → payment 行 INSERT + invoice.paid_at + PAID）。 */
    @EventHandler
    public void on(PaymentRecordedEvent event) {
        paymentMapper.insertPayment(
                event.paymentId(),
                event.invoiceId(),
                event.paidAmount(),
                event.currency(),
                event.paidAt(),
                event.paymentMethod(),
                event.externalReference()
        );
        summaryMapper.updateForPaid(event.invoiceId(), event.paidAt());
        log.info("[local-billing] Payment 投影 invoiceId={} paymentId={} paidAmount={}",
                event.invoiceId(), event.paymentId(), event.paidAmount());
    }

    /** 督促投影（US23 / T4.3、InvoiceOverdueEvent → OVERDUE）。 */
    @EventHandler
    public void on(InvoiceOverdueEvent event) {
        summaryMapper.updateForOverdue(event.invoiceId());
        log.info("[local-billing] Invoice 督促投影 invoiceId={} markedAt={}",
                event.invoiceId(), event.markedAt());
    }
}
