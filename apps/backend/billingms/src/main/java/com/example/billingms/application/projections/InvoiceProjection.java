package com.example.billingms.application.projections;

import com.example.billingms.config.BillingProperties;
import com.example.billingms.domain.events.DiscountAppliedEvent;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.billingms.domain.events.PartialPaymentRecordedEvent;
import com.example.billingms.domain.events.PaymentDetailRecorded;
import com.example.billingms.domain.model.BillingStatus;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceLineMapper;
import com.example.billingms.infrastructure.repositories.mybatis.InvoiceSummaryMapper;
import com.example.billingms.infrastructure.repositories.mybatis.PaymentMapper;
import com.example.shared.events.PaymentRecordedEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Invoice 集約イベントの Read Model 投影集約クラス（IT7 review M1 対応）。
 *
 * <p>イベント別の updateForXxx 呼び分けを本クラスに閉じ込めることで、Mapper API の状態遷移増殖
 * （SRP / DRY 違反、review M1）を解消する。EventHandler は薄いディスパッチ層となり、本クラスが
 * Read Model 更新の単一責務を持つ。</p>
 *
 * <p>将来 InvoiceAdjustedEvent / PaymentDetailRecorded など状態遷移が増えた場合、本クラスに
 * {@code apply(event)} メソッドを 1 つ追加するだけで完結する（OCP）。</p>
 *
 * <p>Note: 本クラスは集約発火型（ADR-0012）の購読側であり、{@code @ProcessingGroup} は
 * EventHandler 側（{@code InvoiceProjectionsEventHandler}）に付与する。</p>
 */
@Component
public class InvoiceProjection {

    private final InvoiceSummaryMapper summaryMapper;
    private final InvoiceLineMapper lineMapper;
    private final PaymentMapper paymentMapper;
    private final String discountDescriptionTemplate;

    public InvoiceProjection(InvoiceSummaryMapper summaryMapper,
                             InvoiceLineMapper lineMapper,
                             PaymentMapper paymentMapper,
                             BillingProperties properties) {
        this.summaryMapper = summaryMapper;
        this.lineMapper = lineMapper;
        this.paymentMapper = paymentMapper;
        this.discountDescriptionTemplate = properties.discountDescription();
    }

    /** PENDING → CALCULATED 初期挿入 + BASIC 行追加。 */
    public void apply(InvoiceCalculatedEvent event) {
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
    }

    /** CALCULATED 自己遷移：割引額確定 + DISCOUNT 行追加。 */
    public void apply(DiscountAppliedEvent event) {
        summaryMapper.updateDiscount(
                event.invoiceId(),
                event.discountAmount(),
                event.totalAmount()
        );
        Integer maxSeq = lineMapper.findMaxLineSeq(event.invoiceId());
        int nextSeq = (maxSeq == null ? 1 : maxSeq + 1);
        BigDecimal lineAmount = event.discountAmount().negate();
        int percent = event.discountRate().multiply(new BigDecimal("100")).intValue();
        String description = String.format(discountDescriptionTemplate, percent);
        lineMapper.insertInvoiceLine(
                event.invoiceId(),
                nextSeq,
                "DISCOUNT",
                description,
                lineAmount,
                "CORPORATE"
        );
    }

    /** CALCULATED → INVOICED：invoice_number + payment_due 確定。 */
    public void apply(InvoiceIssuedEvent event) {
        summaryMapper.updateForIssued(
                event.invoiceId(),
                event.invoiceNumber(),
                event.paymentDue()
        );
    }

    /**
     * INVOICED|OVERDUE → PAID：payment 行 INSERT + paid_at 反映。
     *
     * <p>shared event は cross-service 契約として最小化（payment_method / external_reference を含まない、
     * IT7 review H1 修正）。IT8 で PaymentDetailRecorded 補完 event を導入予定。</p>
     */
    public void apply(PaymentRecordedEvent event) {
        paymentMapper.insertPayment(
                event.paymentId(),
                event.invoiceId(),
                event.paidAmount(),
                event.currency(),
                event.paidAt(),
                null,
                null
        );
        summaryMapper.updateForPaid(event.invoiceId(), event.paidAt());
    }

    /** INVOICED → OVERDUE：billing_status のみ更新。 */
    public void apply(InvoiceOverdueEvent event) {
        summaryMapper.updateForOverdue(event.invoiceId());
    }

    /**
     * INVOICED|PARTIALLY_PAID → PARTIALLY_PAID（IT9 A1.5 / US26、Stripe webhook 部分入金）：
     * payment 行 INSERT（is_partial=TRUE）+ invoice.paid_so_far 加算 + billing_status=PARTIALLY_PAID。
     * 後続の PaymentDetailRecorded で payment_method / external_reference を補完。
     */
    public void apply(PartialPaymentRecordedEvent event) {
        paymentMapper.insertPartialPayment(
                event.paymentId(),
                event.invoiceId(),
                event.paidAmount(),
                event.currency(),
                event.paidAt(),
                null,
                null
        );
        summaryMapper.updateForPartiallyPaid(event.invoiceId(), event.paidAmount());
    }

    /**
     * IT8 T5.1 / ADR-0019: PaymentDetailRecorded 受信時に payment テーブルの
     * payment_method / external_reference を補完 UPDATE する。shared
     * PaymentRecordedEvent → INSERT（method/ref は null）の後に本 event が同 paymentId で UPDATE する。
     */
    public void apply(PaymentDetailRecorded event) {
        paymentMapper.updatePaymentDetail(
                event.paymentId(),
                event.paymentMethod(),
                event.externalReference()
        );
    }
}
