package com.example.billingms.domain.model;

import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.services.FareCalculator;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.time.Clock;
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
 * <p>IT7 Task 2.3 で CalculateInvoiceCommand 受理（PENDING → CALCULATED 遷移）を実装。
 * ApplyDiscountCommand / IssueInvoiceCommand / RecordPaymentCommand / MarkOverdueCommand は
 * Task 3.x / 4.x で順次追加する。</p>
 */
@Aggregate
@SuppressWarnings("unused") // フィールドは Task 3.x / 4.x の Command Handler で利用
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

    /**
     * 輸送料金算出（PENDING → CALCULATED）。{@code CargoDeliveredEvent} 起点で
     * {@code CrossCargoDeliveredEventHandler}（Task 2.4）が本コマンドを発行する。
     *
     * <p>不変条件検証 → FareCalculator で basicAmount 算出 →
     * {@link InvoiceCalculatedEvent} を apply（ADR-0012 集約発火型）。</p>
     */
    @CommandHandler
    public Invoice(CalculateInvoiceCommand command, FareCalculator fareCalculator, Clock clock) {
        if (command.invoiceId() == null || command.invoiceId().isBlank()) {
            throw new IllegalArgumentException("invoiceId は必須です");
        }
        if (command.bookingId() == null || command.bookingId().isBlank()) {
            throw new IllegalArgumentException("bookingId は必須です");
        }
        if (command.shipperId() == null || command.shipperId().isBlank()) {
            throw new IllegalArgumentException("shipperId は必須です");
        }
        if (command.transport() == null) {
            throw new IllegalArgumentException("transport は必須です");
        }
        BigDecimal basicAmount = fareCalculator.calculate(command.transport());
        AggregateLifecycle.apply(new InvoiceCalculatedEvent(
                command.invoiceId(),
                command.bookingId(),
                command.shipperId(),
                basicAmount,
                command.transport().currency(),
                LocalDateTime.now(clock)
        ));
    }

    @EventSourcingHandler
    public void on(InvoiceCalculatedEvent event) {
        this.invoiceId = event.invoiceId();
        this.bookingId = event.bookingId();
        this.shipperId = event.shipperId();
        this.basicAmount = event.basicAmount();
        this.discountAmount = BigDecimal.ZERO;
        this.adjustmentAmount = BigDecimal.ZERO;
        this.totalAmount = event.basicAmount();
        this.currency = event.currency();
        this.billingStatus = BillingStatus.CALCULATED;
    }
}
