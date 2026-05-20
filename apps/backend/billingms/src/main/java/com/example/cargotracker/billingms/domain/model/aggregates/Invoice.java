package com.example.cargotracker.billingms.domain.model.aggregates;

import com.example.cargotracker.billingms.domain.model.commands.CalculateChargeCommand;
import com.example.cargotracker.billingms.domain.model.commands.CreateInvoiceCommand;
import com.example.cargotracker.billingms.domain.model.commands.IssueInvoiceCommand;
import com.example.cargotracker.billingms.domain.model.commands.RecordPaymentCommand;
import com.example.cargotracker.billingms.domain.model.events.ChargeCalculatedEvent;
import com.example.cargotracker.billingms.domain.model.events.InvoiceCreatedEvent;
import com.example.cargotracker.billingms.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billingms.domain.model.events.PaymentRecordedEvent;
import com.example.cargotracker.billingms.domain.model.valueobjects.BillingStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.springframework.context.annotation.Profile;

/**
 * 請求集約（US21-US23）。
 *
 * <p>ライフサイクル: PENDING → CALCULATED → INVOICED → PAID / OVERDUE</p>
 */
@EventSourced(idType = String.class, tagKey = "invoiceId")
@Profile("!springboot-integration-test")
public class Invoice {

    private static final BigDecimal MAX_DISCOUNT_RATE = new BigDecimal("0.30");

    private String invoiceId;
    private BillingStatus status;

    public String getInvoiceId() {
        return invoiceId;
    }

    @EntityCreator
    public Invoice() {
        // Axon が Event 再生で呼び出すデフォルトコンストラクタ
    }

    /**
     * Invoice 作成（作成系コマンド）。
     */
    @CommandHandler
    public static String create(CreateInvoiceCommand command, EventAppender appender) {
        appender.append(new InvoiceCreatedEvent(
                command.invoiceId(),
                command.bookingId(),
                command.shipperId()));
        return command.invoiceId();
    }

    /**
     * 料金算出・確定。
     */
    @CommandHandler
    public void calculateCharge(CalculateChargeCommand command, EventAppender appender) {
        if (status != BillingStatus.PENDING) {
            throw new IllegalStateException(
                    "料金算出は PENDING 状態のみ可能です。現在の状態: " + status);
        }

        BigDecimal discountRate = command.discountRate() != null
                ? command.discountRate() : BigDecimal.ZERO;

        if (discountRate.compareTo(MAX_DISCOUNT_RATE) > 0) {
            throw new IllegalArgumentException(
                    "割引率は 30% 以下である必要があります: " + discountRate);
        }

        BigDecimal discount = command.baseAmount().multiply(discountRate);
        BigDecimal finalAmount = command.baseAmount().subtract(discount)
                .setScale(0, RoundingMode.HALF_UP);

        appender.append(new ChargeCalculatedEvent(
                command.invoiceId(),
                command.baseAmount(),
                discountRate,
                finalAmount,
                command.currencyCode(),
                command.operatorId()));
    }

    /**
     * 精算書発行。CALCULATED 状態のみ可能。
     */
    @CommandHandler
    public void issueInvoice(IssueInvoiceCommand command, EventAppender appender) {
        if (status != BillingStatus.CALCULATED) {
            throw new IllegalStateException(
                    "精算書発行は CALCULATED 状態のみ可能です。現在の状態: " + status);
        }
        String invoiceNumber = generateInvoiceNumber(command);
        appender.append(new InvoiceIssuedEvent(
                command.invoiceId(),
                invoiceNumber,
                command.paymentDue(),
                command.operatorId()));
    }

    /**
     * 入金確認・精算完了。INVOICED 状態のみ可能。
     */
    @CommandHandler
    public void recordPayment(RecordPaymentCommand command, EventAppender appender) {
        if (status != BillingStatus.INVOICED) {
            throw new IllegalStateException(
                    "入金確認は INVOICED 状態のみ可能です。現在の状態: " + status);
        }
        appender.append(new PaymentRecordedEvent(
                command.invoiceId(),
                command.paidAmount(),
                command.paymentMethod(),
                LocalDateTime.now(),
                command.operatorId()));
    }

    @EventSourcingHandler
    public void on(InvoiceCreatedEvent event) {
        this.invoiceId = event.invoiceId();
        this.status = BillingStatus.PENDING;
    }

    @EventSourcingHandler
    public void on(ChargeCalculatedEvent event) {
        this.status = BillingStatus.CALCULATED;
    }

    @EventSourcingHandler
    public void on(InvoiceIssuedEvent event) {
        this.status = BillingStatus.INVOICED;
    }

    @EventSourcingHandler
    public void on(PaymentRecordedEvent event) {
        this.status = BillingStatus.PAID;
    }

    private static String generateInvoiceNumber(IssueInvoiceCommand command) {
        String date = command.paymentDue() != null
                ? command.paymentDue().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "INV-" + date + "-" + String.format("%06d", System.nanoTime() % 1_000_000);
    }
}
