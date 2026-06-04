package com.example.billingms.domain.model;

import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 請求書集約（{@code Invoice}、US21 / US22 / US23、domain-model.md L893-908）。
 *
 * <p>Billing Context の唯一の集約。配送完了（{@code CargoDeliveredEvent}、ADR-0012/0015）
 * 起点で {@link BillingStatus#PENDING} → {@link BillingStatus#CALCULATED} に遷移し、
 * その後 {@link BillingStatus#INVOICED} → {@link BillingStatus#PAID} または
 * {@link BillingStatus#OVERDUE} のステートマシンを進む。状態遷移ルールは
 * {@link BillingStatus#canTransitionTo(BillingStatus)} に閉じる。</p>
 *
 * <p>不変条件（domain-model.md L960-966）:</p>
 * <ul>
 *   <li>{@code totalAmount = basicAmount - discountAmount + adjustmentAmount}</li>
 *   <li>{@code billingStatus = PAID} 遷移時、{@code paidAt} は必須</li>
 *   <li>通貨は集約内で一貫（混在不可）</li>
 *   <li>{@code paymentDue} は {@link BillingStatus#INVOICED} 遷移時に確定</li>
 *   <li>{@link BillingStatus#CANCELLED} 状態の Invoice は再発行不可（新規 Invoice を発行する）</li>
 * </ul>
 *
 * <p><strong>本クラスは IT7 Task 2.1 で骨格のみ実装。Command Handler と
 * EventSourcingHandler は Task 2.3（CalculateInvoiceCommand）/ Task 3.x（ApplyDiscount）/
 * Task 4.x（IssueInvoice / RecordPayment / MarkOverdue）で順次追加する。</strong></p>
 */
@Aggregate
@SuppressWarnings("unused") // フィールドは Task 2.3 以降の @CommandHandler / @EventSourcingHandler で利用
public class Invoice {

    @AggregateIdentifier
    private String invoiceId;
    private String bookingId;
    private String shipperId;
    private BigDecimal basicAmount;
    private BigDecimal discountAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal totalAmount;
    private String currency;
    private BillingStatus billingStatus;
    private String invoiceNumber;
    private LocalDate paymentDue;
    private LocalDateTime paidAt;

    protected Invoice() {
        // Axon required no-arg constructor
    }
}
