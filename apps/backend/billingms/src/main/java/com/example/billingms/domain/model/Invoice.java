package com.example.billingms.domain.model;

import com.example.billingms.domain.commands.ApplyDiscountCommand;
import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.commands.IssueInvoiceCommand;
import com.example.billingms.domain.commands.MarkOverdueCommand;
import com.example.billingms.domain.commands.RecordPartialPaymentCommand;
import com.example.billingms.domain.commands.RecordPaymentCommand;
import com.example.billingms.domain.events.DiscountAppliedEvent;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.billingms.domain.events.PartialPaymentRecordedEvent;
import com.example.billingms.domain.services.CorporateDiscountPolicy;
import com.example.billingms.domain.services.FareCalculator;
import com.example.billingms.domain.services.InvoiceNumberGenerator;
import com.example.billingms.domain.services.PaymentDuePolicy;
import com.example.billingms.infrastructure.outboundservices.ShipperInfoAcl;
import com.example.billingms.domain.model.CorporateContract;
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
    /** 入金残額追跡（IT9 A1.3 / ADR-0020 / US26、部分入金を含む累積入金を保持）。 */
    private BalanceTracker balance;

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
        this.balance = BalanceTracker.of(event.basicAmount());
    }

    /**
     * 法人割引適用（CALCULATED → CALCULATED 自己遷移、US22 / Task 3.3）。
     *
     * <p>{@link ShipperInfoAcl} で {@link CorporateContract} を取得し、
     * {@link CorporateDiscountPolicy} で割引額を算出して
     * {@link DiscountAppliedEvent} を集約発火する（ADR-0012）。</p>
     *
     * <p>状態遷移: CALCULATED → CALCULATED（{@link BillingStatus#canTransitionTo} で許可）。
     * 既に割引適用済みでも再度上書き可能（割引率変更時の再計算を許容）。</p>
     */
    @CommandHandler
    public void handle(ApplyDiscountCommand command,
                       ShipperInfoAcl shipperInfoAcl,
                       CorporateDiscountPolicy discountPolicy,
                       Clock clock) {
        if (this.billingStatus != BillingStatus.CALCULATED) {
            throw new IllegalStateException(
                    "ApplyDiscountCommand は CALCULATED 状態でのみ受理可能です: " + this.billingStatus);
        }
        // IT8 T4.2: manualDiscountRate 指定時は ACL を呼ばず CORPORATE 扱いで直接適用
        // （Circuit Breaker OPEN 時に S23 で経理担当者が手動入力するケース）
        CorporateContract contract = command.manualDiscountRate() != null
                ? new CorporateContract(this.shipperId,
                        com.example.billingms.domain.model.ShipperType.CORPORATE,
                        command.manualDiscountRate())
                : shipperInfoAcl.getContract(this.shipperId);
        BigDecimal discountAmount = discountPolicy.calculateDiscount(this.basicAmount, contract);
        BigDecimal newTotal = this.basicAmount
                .subtract(discountAmount)
                .add(this.adjustmentAmount);
        AggregateLifecycle.apply(new DiscountAppliedEvent(
                this.invoiceId,
                this.shipperId,
                contract.discountRate(),
                discountAmount,
                newTotal,
                LocalDateTime.now(clock)
        ));
    }

    @EventSourcingHandler
    public void on(DiscountAppliedEvent event) {
        this.discountAmount = event.discountAmount();
        this.totalAmount = event.totalAmount();
        if (this.balance != null) {
            this.balance = this.balance.withTotalDue(event.totalAmount());
        }
    }

    /**
     * 精算書発行（CALCULATED → INVOICED、US23 / T4.1）。
     *
     * <p>{@link InvoiceNumberGenerator} で invoice_number を採番、{@link PaymentDuePolicy} で
     * 支払期限を確定し、{@link InvoiceIssuedEvent} を集約発火する（ADR-0012）。状態遷移は
     * {@link BillingStatus#canTransitionTo(BillingStatus)} で検証する。</p>
     */
    @CommandHandler
    public void handle(IssueInvoiceCommand command,
                       InvoiceNumberGenerator numberGenerator,
                       PaymentDuePolicy paymentDuePolicy,
                       Clock clock) {
        if (!this.billingStatus.canTransitionTo(BillingStatus.INVOICED)) {
            throw new IllegalStateException(
                    "IssueInvoiceCommand は CALCULATED 状態でのみ受理可能です: " + this.billingStatus);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        String invoiceNumber = numberGenerator.next();
        LocalDate paymentDue = paymentDuePolicy.calculateDueDate(today);
        AggregateLifecycle.apply(new InvoiceIssuedEvent(
                this.invoiceId,
                this.shipperId,
                invoiceNumber,
                paymentDue,
                this.totalAmount,
                now
        ));
    }

    @EventSourcingHandler
    public void on(InvoiceIssuedEvent event) {
        this.invoiceNumber = event.invoiceNumber();
        this.paymentDue = event.paymentDue();
        this.billingStatus = BillingStatus.INVOICED;
    }

    /**
     * 入金記録（INVOICED|OVERDUE → PAID、US23 / T4.1）。
     *
     * <p>状態遷移検証 + 通貨整合性検証 + 金額完全一致検証（IT7 は完全一致のみ、IT8 で部分入金対応）。
     * shared {@link com.example.shared.events.PaymentRecordedEvent} を集約発火する（ADR-0012 集約発火型、
     * 二段イベント回避）。bookingId を Event payload に含めることで
     * bookingms cross-service（T4.5）が {@code Cargo} 集約を {@code SETTLED} へ遷移できる。</p>
     */
    @CommandHandler
    public void handle(RecordPaymentCommand command, Clock clock) {
        if (!this.billingStatus.canTransitionTo(BillingStatus.PAID)) {
            throw new IllegalStateException(
                    "RecordPaymentCommand は INVOICED または OVERDUE 状態でのみ受理可能です: "
                            + this.billingStatus);
        }
        if (command.paidAmount() == null) {
            throw new IllegalArgumentException("paidAmount は必須です");
        }
        if (command.paidAmount().compareTo(this.totalAmount) != 0) {
            throw new IllegalArgumentException(
                    "paidAmount は totalAmount と完全一致が必要です（IT7 制約、IT8 で部分入金対応）: "
                            + "expected=" + this.totalAmount + ", actual=" + command.paidAmount());
        }
        if (command.currency() == null || !command.currency().equals(this.currency)) {
            throw new IllegalArgumentException(
                    "currency は集約通貨と一致が必要です: expected=" + this.currency
                            + ", actual=" + command.currency());
        }
        if (command.paymentId() == null || command.paymentId().isBlank()) {
            throw new IllegalArgumentException("paymentId は必須です");
        }
        AggregateLifecycle.apply(new com.example.shared.events.PaymentRecordedEvent(
                this.invoiceId,
                command.paymentId(),
                this.bookingId,
                this.shipperId,
                command.paidAmount(),
                command.currency(),
                command.paidAt(),
                LocalDateTime.now(clock)
        ));
        // IT8 T5.1 / ADR-0019: 内部 event で payment_method / external_reference を補完。
        // paymentMethod / externalReference のいずれかが非 null の場合のみ apply（無用な投影 UPDATE 回避）
        if (command.paymentMethod() != null || command.externalReference() != null) {
            AggregateLifecycle.apply(new com.example.billingms.domain.events.PaymentDetailRecorded(
                    this.invoiceId,
                    command.paymentId(),
                    command.paymentMethod(),
                    command.externalReference()
            ));
        }
    }

    /**
     * 集約発火型（ADR-0012）に準拠し、shared/events の {@link com.example.shared.events.PaymentRecordedEvent}
     * を直接 apply する。bookingms cross-service が同 event を購読して Cargo を SETTLED に遷移させる
     * （T4.5）。投影・通知も本 event を購読することで二段イベント（H1）を回避する。
     */
    @EventSourcingHandler
    public void on(com.example.shared.events.PaymentRecordedEvent event) {
        this.paidAt = event.paidAt();
        this.billingStatus = BillingStatus.PAID;
        if (this.balance != null) {
            // 完全入金として扱う（IT7 制約：paidAmount == totalAmount を Command Handler で検証済み）
            BigDecimal remaining = this.balance.remainingBalance();
            if (remaining.signum() > 0) {
                this.balance = this.balance.apply(remaining);
            }
        }
    }

    /**
     * 督促（INVOICED → OVERDUE、US23 / T4.1 / T4.6）。
     *
     * <p>{@code OverdueScheduler}（T4.6）が支払期限超過判定を実施した後で本コマンドを発行する。
     * 集約は状態遷移のみ実施し、{@link InvoiceOverdueEvent} を集約発火する。</p>
     */
    @CommandHandler
    public void handle(MarkOverdueCommand command, Clock clock) {
        if (!this.billingStatus.canTransitionTo(BillingStatus.OVERDUE)) {
            throw new IllegalStateException(
                    "MarkOverdueCommand は INVOICED 状態でのみ受理可能です: " + this.billingStatus);
        }
        AggregateLifecycle.apply(new InvoiceOverdueEvent(
                this.invoiceId,
                this.shipperId,
                LocalDateTime.now(clock)
        ));
    }

    @EventSourcingHandler
    public void on(InvoiceOverdueEvent event) {
        this.billingStatus = BillingStatus.OVERDUE;
    }

    /**
     * 部分入金記録（INVOICED | PARTIALLY_PAID → PARTIALLY_PAID | PAID、IT9 A1.4 / ADR-0020 / US26）。
     *
     * <p>Stripe webhook 経由の部分入金を受理する。受理可否は BalanceTracker と BillingStatus で判定:</p>
     * <ul>
     *   <li>状態が INVOICED または PARTIALLY_PAID 以外: {@link IllegalStateException}</li>
     *   <li>通貨が集約通貨と不一致: {@link IllegalArgumentException}</li>
     *   <li>残額を超過する入金: BalanceTracker.apply が {@link IllegalArgumentException}</li>
     * </ul>
     *
     * <p>残額がゼロになる場合は PAID 遷移として shared {@code PaymentRecordedEvent} を発火し、
     * bookingms cross-service が Cargo を SETTLED に遷移できるようにする。残額が残る場合は
     * billingms 内部の {@link PartialPaymentRecordedEvent} のみを発火する。</p>
     */
    @CommandHandler
    public void handle(RecordPartialPaymentCommand command, Clock clock) {
        if (this.billingStatus != BillingStatus.INVOICED
                && this.billingStatus != BillingStatus.PARTIALLY_PAID) {
            throw new IllegalStateException(
                    "RecordPartialPaymentCommand は INVOICED または PARTIALLY_PAID 状態でのみ受理可能です: "
                            + this.billingStatus);
        }
        if (command.paymentId() == null || command.paymentId().isBlank()) {
            throw new IllegalArgumentException("paymentId は必須です");
        }
        if (command.paidAmount() == null || command.paidAmount().signum() <= 0) {
            throw new IllegalArgumentException("paidAmount は 0 より大きい値で必須です");
        }
        if (command.currency() == null || !command.currency().equals(this.currency)) {
            throw new IllegalArgumentException(
                    "currency は集約通貨と一致が必要です: expected=" + this.currency
                            + ", actual=" + command.currency());
        }
        // BalanceTracker.apply で残額超過を検証（IllegalArgumentException）
        BalanceTracker newBalance = this.balance.apply(command.paidAmount());
        LocalDateTime now = LocalDateTime.now(clock);

        if (newBalance.isFullyPaid()) {
            // 残額入金 → PAID 遷移。shared event で cross-service 通知
            AggregateLifecycle.apply(new com.example.shared.events.PaymentRecordedEvent(
                    this.invoiceId,
                    command.paymentId(),
                    this.bookingId,
                    this.shipperId,
                    command.paidAmount(),
                    command.currency(),
                    command.paidAt(),
                    now
            ));
        } else {
            // 部分入金 → PARTIALLY_PAID 遷移（または維持）。内部 event のみ
            AggregateLifecycle.apply(new PartialPaymentRecordedEvent(
                    this.invoiceId,
                    command.paymentId(),
                    this.bookingId,
                    this.shipperId,
                    command.paidAmount(),
                    command.currency(),
                    command.paidAt(),
                    newBalance.paidSoFar(),
                    newBalance.totalDue(),
                    now
            ));
        }

        // payment_method / external_reference 補完（A1.4 + M4 統合）
        if (command.paymentMethod() != null || command.externalReference() != null) {
            AggregateLifecycle.apply(new com.example.billingms.domain.events.PaymentDetailRecorded(
                    this.invoiceId,
                    command.paymentId(),
                    command.paymentMethod(),
                    command.externalReference()
            ));
        }
    }

    @EventSourcingHandler
    public void on(PartialPaymentRecordedEvent event) {
        this.billingStatus = BillingStatus.PARTIALLY_PAID;
        if (this.balance != null) {
            this.balance = this.balance.apply(event.paidAmount());
        }
    }
}
